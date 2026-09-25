package com.ustb.seforge.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.analytics.api.DashboardView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional differential materialization. Business writes are not coupled to Java listeners.
 * Row contributions are retracted before replacement; repeated events therefore add nothing.
 * Structural changes revisit dependent families (including FK cascades, which MySQL does not
 * trigger). Ordinary activity revisits only its own row, not the complete dashboard.
 */
@Service
public class AnalyticsProjectionService {
    private static final List<String> SOURCES = List.of("course_members", "assignment", "submission",
            "grade", "feedback", "conversation_message", "tutor_interaction", "answer_feedback", "review_job");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AnalyticsProjectionService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional
    public String refresh(Long courseId) {
        // Acquire an exclusive row lock immediately: INSERT IGNORE can give concurrent
        // refreshers shared duplicate-key locks which deadlock on the FOR UPDATE upgrade.
        jdbc.update("INSERT INTO analytics_projection(course_id) VALUES(?) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id)", courseId);
        long revision = jdbc.queryForObject("SELECT revision FROM analytics_projection WHERE course_id=? FOR UPDATE", Long.class, courseId);
        // Do not use an event ID high-water mark: a lower ID transaction can commit later.
        List<Map<String,Object>> events = jdbc.queryForList("SELECT id,source_type,source_id FROM analytics_change WHERE course_id=? AND applied=false ORDER BY id", courseId);
        if (events.isEmpty()) return "delta-" + revision;
        Map<String,Set<Long>> affected = new LinkedHashMap<>();
        Set<String> families = new LinkedHashSet<>();
        for (var event : events) {
            String type = str(event,"source_type"); long id = num(event,"source_id");
            if (type.equals("async_job")) {
                jdbc.queryForList("SELECT id FROM review_job WHERE course_id=? AND async_job_id=?", Long.class, courseId,id)
                        .forEach(key -> affected.computeIfAbsent("review_job", ignored -> new TreeSet<>()).add(key));
            } else if (type.equals("submission")) {
                families.addAll(List.of("submission","grade","feedback","review_job"));
            } else if (type.equals("grade")) {
                affected.computeIfAbsent(type, ignored -> new TreeSet<>()).add(id); families.add("feedback");
            } else if (SOURCES.contains(type) && !type.equals("course_members") && !type.equals("assignment")) {
                affected.computeIfAbsent(type, ignored -> new TreeSet<>()).add(id);
                // Deleting a message cascades answer feedback without invoking its trigger.
                if (type.equals("conversation_message")) families.add("answer_feedback");
            } else families.addAll(SOURCES);
        }
        for (String type : families) {
            Set<Long> ids = affected.computeIfAbsent(type, ignored -> new TreeSet<>());
            String from = type.equals("feedback") ? "feedback x JOIN grade g ON g.id=x.grade_id WHERE g.course_id=?" : type + " x WHERE x.course_id=?";
            ids.addAll(jdbc.queryForList("SELECT x.id FROM " + from, Long.class, courseId));
            ids.addAll(jdbc.queryForList("SELECT source_id FROM analytics_contribution WHERE course_id=? AND source_type=?", Long.class,courseId,type));
        }
        for (var family : affected.entrySet()) for (Long id : family.getValue()) replace(courseId,family.getKey(),id);
        revision += events.size();
        for (var event : events) jdbc.update("UPDATE analytics_change SET applied=true,applied_revision=? WHERE id=? AND course_id=?",revision,num(event,"id"),courseId);
        jdbc.update("UPDATE analytics_projection SET revision=? WHERE course_id=?",revision,courseId);
        return "delta-" + revision;
    }

    private void replace(long course, String type, long id) {
        List<String> stored = jdbc.queryForList("SELECT payload FROM analytics_contribution WHERE course_id=? AND source_type=? AND source_id=?",String.class,course,type,id);
        List<Fact> before = stored.isEmpty() ? List.of() : decode(stored.getFirst());
        List<Fact> after = facts(course,type,id);
        if (before.equals(after)) return;
        Map<Key,BigDecimal> delta = new LinkedHashMap<>();
        before.forEach(f -> delta.merge(new Key(f.scope,f.metric,f.dimension),f.amount.negate(),BigDecimal::add));
        after.forEach(f -> delta.merge(new Key(f.scope,f.metric,f.dimension),f.amount,BigDecimal::add));
        delta.forEach((key,value) -> {
            if (value.signum()!=0) jdbc.update("INSERT INTO analytics_total(course_id,scope_id,metric,dimension_key,amount) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount)",course,key.scope,key.metric,key.dimension,value);
        });
        if (after.isEmpty()) jdbc.update("DELETE FROM analytics_contribution WHERE course_id=? AND source_type=? AND source_id=?",course,type,id);
        else jdbc.update("INSERT INTO analytics_contribution(course_id,source_type,source_id,payload) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE payload=VALUES(payload)",course,type,id,encode(after));
    }

    private List<Fact> facts(long course,String type,long id) {
        String from = type.equals("feedback") ? "feedback x JOIN grade g ON g.id=x.grade_id WHERE g.course_id=? AND x.id=?" : type+" x WHERE x.course_id=? AND x.id=?";
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT x.* FROM "+from,course,id);
        if (rows.isEmpty()) return List.of();
        Map<String,Object> row=rows.getFirst(); List<Fact> result=new ArrayList<>();
        Long classId=nullable(row,"class_id");
        switch(type) {
            case "course_members" -> {
                if (!"ACTIVE".equals(str(row,"status")) || !"STUDENT".equals(str(row,"role"))) break;
                add(result,classId,"students","",BigDecimal.ONE);
                long count=jdbc.queryForObject("SELECT COUNT(*) FROM assignment WHERE course_id=? AND status IN ('PUBLISHED','CLOSED','ARCHIVED') AND (class_id IS NULL OR class_id=?)",Long.class,course,classId);
                add(result,classId,"expected","",BigDecimal.valueOf(count));
            }
            case "assignment" -> {
                if (!List.of("PUBLISHED","CLOSED","ARCHIVED").contains(str(row,"status"))) break;
                add(result,classId,"assignments","",BigDecimal.ONE);
                if (classId==null) for(long scope:classes(course)) result.add(new Fact(scope,"assignments","",BigDecimal.ONE));
            }
            case "submission" -> {
                // One completion per currently eligible student/assignment, irrespective of attempts.
                if (!List.of("SUBMITTED","GRADED").contains(str(row,"status"))) break;
                var eligible=jdbc.queryForList("SELECT cm.class_id FROM course_members cm JOIN assignment a ON a.course_id=cm.course_id WHERE cm.course_id=? AND cm.user_id=? AND cm.role='STUDENT' AND cm.status='ACTIVE' AND a.id=? AND a.status IN ('PUBLISHED','CLOSED','ARCHIVED') AND (a.class_id IS NULL OR a.class_id=cm.class_id)",course,num(row,"user_id"),num(row,"assignment_id"));
                if (eligible.isEmpty()) break;
                Long first=jdbc.queryForObject("SELECT MIN(id) FROM submission WHERE course_id=? AND assignment_id=? AND user_id=? AND status IN ('SUBMITTED','GRADED')",Long.class,course,num(row,"assignment_id"),num(row,"user_id"));
                if (Objects.equals(first,id)) add(result,nullable(eligible.getFirst(),"class_id"),"completed","",BigDecimal.ONE);
            }
            case "grade" -> {
                if (!"CONFIRMED".equals(str(row,"status"))) break;
                var values=jdbc.queryForList("SELECT s.class_id,r.total_score FROM submission s JOIN rubric r ON r.assignment_id=s.assignment_id WHERE s.id=? AND s.course_id=?",num(row,"submission_id"),course);
                if (values.isEmpty()) break; var value=values.getFirst();
                BigDecimal score=ratio(decimal(row,"final_score"),decimal(value,"total_score"));
                Long scope=nullable(value,"class_id"); add(result,scope,"gradeCount","",BigDecimal.ONE); add(result,scope,"gradeSum","",score);
                String bucket=score.compareTo(BigDecimal.valueOf(60))<0?"0-59":score.compareTo(BigDecimal.valueOf(70))<0?"60-69":score.compareTo(BigDecimal.valueOf(80))<0?"70-79":score.compareTo(BigDecimal.valueOf(90))<0?"80-89":"90-100";
                add(result,scope,"bucket",bucket,BigDecimal.ONE);
            }
            case "feedback" -> {
                if (row.get("final_score")==null || row.get("rubric_item_id")==null) break;
                var values=jdbc.queryForList("SELECT s.class_id,kp.id,kp.title,ri.max_score FROM grade g JOIN submission s ON s.id=g.submission_id JOIN rubric_item ri ON ri.id=? JOIN assignment_question q ON q.id=ri.question_id AND q.assignment_id=s.assignment_id JOIN knowledge_points kp ON kp.id=q.knowledge_point_id AND kp.course_id=g.course_id WHERE g.id=? AND g.course_id=? AND g.status='CONFIRMED'",num(row,"rubric_item_id"),num(row,"grade_id"),course);
                if (values.isEmpty()) break; var value=values.getFirst(); String key=num(value,"id")+"|"+str(value,"title"); Long scope=nullable(value,"class_id");
                add(result,scope,"kpEarned",key,decimal(row,"final_score")); add(result,scope,"kpPossible",key,decimal(value,"max_score")); add(result,scope,"kpCount",key,BigDecimal.ONE);
            }
            case "conversation_message" -> {
                if (!"USER".equals(str(row,"role")) || !"COMPLETE".equals(str(row,"status"))) break;
                Long owner=jdbc.queryForObject("SELECT owner_id FROM conversation WHERE id=? AND course_id=?",Long.class,num(row,"conversation_id"),course);
                String excerpt=str(row,"content"); excerpt=excerpt.substring(0,Math.min(160,excerpt.length()));
                add(result,memberClass(course,owner),"question",excerpt,BigDecimal.ONE);
            }
            case "tutor_interaction" -> {
                Long scope=memberClass(course,num(row,"user_id"));
                add(result,scope,"tutor","",BigDecimal.ONE); add(result,scope,"operation",str(row,"operation"),BigDecimal.ONE);
                if ("FAILED".equals(str(row,"status"))) add(result,scope,"tutorFailed","",BigDecimal.ONE);
            }
            case "answer_feedback" -> add(result,memberClass(course,num(row,"user_id")),"rating",str(row,"rating"),BigDecimal.ONE);
            case "review_job" -> {
                String status=str(row,"status");
                if (nullable(row,"async_job_id")!=null) {
                    var states=jdbc.queryForList("SELECT status FROM async_job WHERE id=? AND course_id=?",String.class,num(row,"async_job_id"),course);
                    if (!states.isEmpty()) status=states.getFirst();
                }
                if (!List.of("FAILED","DEAD_LETTER").contains(status)) break;
                Long submission=nullable(row,"submission_id"), assignment=nullable(row,"assignment_id");
                if(submission!=null) { var values=jdbc.queryForList("SELECT class_id FROM submission WHERE id=? AND course_id=?",submission,course); if(!values.isEmpty()) classId=nullable(values.getFirst(),"class_id"); }
                else if(assignment!=null) { var values=jdbc.queryForList("SELECT class_id FROM assignment WHERE id=? AND course_id=?",assignment,course); if(!values.isEmpty()) classId=nullable(values.getFirst(),"class_id"); }
                add(result,classId,"reviewFailed","",BigDecimal.ONE);
                if(submission==null && assignment!=null && classId==null) for(long scope:classes(course)) result.add(new Fact(scope,"reviewFailed","",BigDecimal.ONE));
            }
            default -> throw new IllegalArgumentException("Unknown analytics source");
        }
        return result;
    }

    @Transactional(readOnly=true)
    public DashboardView view(long course,Long classId) {
        Map<String,Map<String,BigDecimal>> totals=new HashMap<>();
        jdbc.queryForList("SELECT metric,dimension_key,amount FROM analytics_total WHERE course_id=? AND scope_id=? AND amount<>0",course,classId==null?0:classId)
                .forEach(r->totals.computeIfAbsent(str(r,"metric"),k->new TreeMap<>()).put(str(r,"dimension_key"),decimal(r,"amount")));
        var buckets=map(totals,"bucket").entrySet().stream().map(e->new DashboardView.GradeBucket(e.getKey(),e.getValue().longValue())).toList();
        var knowledge=map(totals,"kpCount").entrySet().stream().map(e->{String[] key=e.getKey().split("\\|",2); BigDecimal rate=ratio(map(totals,"kpEarned").getOrDefault(e.getKey(),BigDecimal.ZERO),map(totals,"kpPossible").getOrDefault(e.getKey(),BigDecimal.ZERO)); return new DashboardView.KnowledgePointMetric(Long.valueOf(key[0]),key[1],rate,e.getValue().longValue(),rate.compareTo(BigDecimal.valueOf(60))<0);}).sorted(Comparator.comparing(DashboardView.KnowledgePointMetric::scoreRate)).toList();
        var questions=map(totals,"question").entrySet().stream().sorted(Map.Entry.<String,BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(10).map(e->new DashboardView.FrequentQuestion(e.getKey(),e.getValue().longValue())).toList();
        Map<String,Long> operations=new TreeMap<>(); map(totals,"operation").forEach((key,value)->operations.put(key,value.longValue()));
        BigDecimal helpful=map(totals,"rating").getOrDefault("HELPFUL",BigDecimal.ZERO), notHelpful=map(totals,"rating").getOrDefault("NOT_HELPFUL",BigDecimal.ZERO);
        return new DashboardView(course,classId,Instant.now(),new DashboardView.Overview(value(totals,"students").longValue(),value(totals,"assignments").longValue(),value(totals,"expected").longValue(),value(totals,"completed").longValue(),ratio(value(totals,"completed"),value(totals,"expected")),divide(value(totals,"gradeSum"),value(totals,"gradeCount"))),buckets,knowledge,questions,new DashboardView.TutorMetrics(value(totals,"tutor").longValue(),value(totals,"tutorFailed").longValue(),operations),new DashboardView.QaFeedbackMetrics(helpful.longValue(),notHelpful.longValue(),ratio(helpful,helpful.add(notHelpful))),new DashboardView.ErrorMetrics(value(totals,"tutorFailed").longValue(),value(totals,"reviewFailed").longValue()));
    }
    private Map<String,BigDecimal> map(Map<String,Map<String,BigDecimal>> totals,String key){return totals.getOrDefault(key,Map.of());}
    private BigDecimal value(Map<String,Map<String,BigDecimal>> totals,String key){return map(totals,key).getOrDefault("",BigDecimal.ZERO);}
    private List<Long> classes(long course){return jdbc.queryForList("SELECT id FROM course_classes WHERE course_id=? ORDER BY id",Long.class,course);}
    private Long memberClass(long course,Long user){var rows=jdbc.queryForList("SELECT class_id FROM course_members WHERE course_id=? AND user_id=? AND status='ACTIVE'",course,user);return rows.isEmpty()?null:nullable(rows.getFirst(),"class_id");}
    private void add(List<Fact> facts,Long scope,String metric,String dimension,BigDecimal amount){facts.add(new Fact(0,metric,dimension,amount));if(scope!=null)facts.add(new Fact(scope,metric,dimension,amount));}
    private static String str(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    private static long num(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static Long nullable(Map<String,Object> row,String key){return row.get(key)==null?null:num(row,key);}
    private static BigDecimal decimal(Map<String,Object> row,String key){return row.get(key)==null?BigDecimal.ZERO:new BigDecimal(row.get(key).toString());}
    private static BigDecimal ratio(BigDecimal a,BigDecimal b){return divide(a.multiply(BigDecimal.valueOf(100)),b);}
    private static BigDecimal divide(BigDecimal a,BigDecimal b){return b.signum()==0?BigDecimal.ZERO:a.divide(b,2,RoundingMode.HALF_UP);}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Analytics serialization failed");}}
    private List<Fact> decode(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Analytics contribution unreadable");}}
    public record Fact(long scope,String metric,String dimension,BigDecimal amount){}
    private record Key(long scope,String metric,String dimension){}
}
