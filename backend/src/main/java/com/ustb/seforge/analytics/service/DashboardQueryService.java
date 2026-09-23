package com.ustb.seforge.analytics.service;

import com.ustb.seforge.analytics.api.DashboardView;
import com.ustb.seforge.analytics.api.DashboardView.ErrorMetrics;
import com.ustb.seforge.analytics.api.DashboardView.FrequentQuestion;
import com.ustb.seforge.analytics.api.DashboardView.GradeBucket;
import com.ustb.seforge.analytics.api.DashboardView.KnowledgePointMetric;
import com.ustb.seforge.analytics.api.DashboardView.Overview;
import com.ustb.seforge.analytics.api.DashboardView.QaFeedbackMetrics;
import com.ustb.seforge.analytics.api.DashboardView.TutorMetrics;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardQueryService {
    private final JdbcTemplate jdbc;

    public DashboardQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    void validateScope(Long courseId, Long classId) {
        requireClass(courseId, classId);
    }

    @Transactional(readOnly = true)
    DashboardView aggregate(Long courseId, Long classId) {
        requireClass(courseId, classId);
        long students = count("SELECT COUNT(*) FROM course_members cm "
                        + "WHERE cm.course_id=? AND cm.role='STUDENT' AND cm.status='ACTIVE'"
                        + classClause("cm.class_id", classId),
                args(courseId, classId));
        long assignmentCount = count("SELECT COUNT(*) FROM assignment a "
                        + "WHERE a.course_id=? AND a.status IN ('PUBLISHED','CLOSED','ARCHIVED')"
                        + assignmentClause(classId),
                args(courseId, classId));
        long expected = count("SELECT COUNT(*) FROM assignment a "
                        + "JOIN course_members cm ON cm.course_id=a.course_id "
                        + "AND cm.role='STUDENT' AND cm.status='ACTIVE' "
                        + "AND (a.class_id IS NULL OR a.class_id=cm.class_id) "
                        + "WHERE a.course_id=? AND a.status IN ('PUBLISHED','CLOSED','ARCHIVED')"
                        + classClause("cm.class_id", classId),
                args(courseId, classId));
        long completed = count("SELECT COUNT(*) FROM (SELECT s.assignment_id,s.user_id "
                        + "FROM submission s JOIN assignment a ON a.id=s.assignment_id "
                        + "WHERE s.course_id=? AND s.status IN ('SUBMITTED','GRADED')"
                        + submissionClassClause(classId)
                        + " GROUP BY s.assignment_id,s.user_id) completed_submissions",
                submissionArgs(courseId, classId));
        BigDecimal average = decimal("SELECT AVG(g.final_score*100/NULLIF(r.total_score,0)) FROM grade g "
                        + "JOIN submission s ON s.id=g.submission_id "
                        + "JOIN rubric r ON r.assignment_id=s.assignment_id "
                        + "WHERE g.course_id=? AND g.status='CONFIRMED'"
                        + classClause("s.class_id", classId),
                args(courseId, classId));
        BigDecimal completionRate = expected == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(expected), 2, RoundingMode.HALF_UP);
        Overview overview = new Overview(students, assignmentCount, expected, completed,
                completionRate, average);
        return new DashboardView(courseId, classId, Instant.now(), overview,
                gradeDistribution(courseId, classId), knowledgePoints(courseId, classId),
                frequentQuestions(courseId, classId), tutor(courseId, classId),
                qaFeedback(courseId, classId), errors(courseId, classId));
    }

    private List<GradeBucket> gradeDistribution(Long courseId, Long classId) {
        String sql = "SELECT CASE WHEN normalized_score<60 THEN '0-59' "
                + "WHEN normalized_score<70 THEN '60-69' WHEN normalized_score<80 THEN '70-79' "
                + "WHEN normalized_score<90 THEN '80-89' ELSE '90-100' END score_bucket,COUNT(*) total "
                + "FROM (SELECT g.final_score*100/NULLIF(r.total_score,0) normalized_score "
                + "FROM grade g JOIN submission s ON s.id=g.submission_id "
                + "JOIN rubric r ON r.assignment_id=s.assignment_id "
                + "WHERE g.course_id=? AND g.status='CONFIRMED'"
                + classClause("s.class_id", classId)
                + ") normalized_grades GROUP BY score_bucket ORDER BY MIN(normalized_score)";
        return jdbc.query(sql, (rs, row) -> new GradeBucket(
                rs.getString("score_bucket"), rs.getLong("total")), args(courseId, classId));
    }

    private List<KnowledgePointMetric> knowledgePoints(Long courseId, Long classId) {
        String sql = "SELECT kp.id,kp.title,COUNT(f.id) evaluated_items,"
                + "SUM(f.final_score) earned,SUM(ri.max_score) possible "
                + "FROM knowledge_points kp "
                + "JOIN assignment_question aq ON aq.knowledge_point_id=kp.id "
                + "JOIN rubric_item ri ON ri.question_id=aq.id "
                + "JOIN feedback f ON f.rubric_item_id=ri.id AND f.final_score IS NOT NULL "
                + "JOIN grade g ON g.id=f.grade_id AND g.status='CONFIRMED' "
                + "JOIN submission s ON s.id=g.submission_id "
                + "WHERE kp.course_id=?" + classClause("s.class_id", classId)
                + " GROUP BY kp.id,kp.title ORDER BY (SUM(f.final_score)/NULLIF(SUM(ri.max_score),0)),kp.id";
        return jdbc.query(sql, (rs, row) -> {
            BigDecimal earned = rs.getBigDecimal("earned");
            BigDecimal possible = rs.getBigDecimal("possible");
            BigDecimal rate = possible == null || possible.signum() == 0 ? BigDecimal.ZERO
                    : earned.multiply(BigDecimal.valueOf(100)).divide(possible, 2, RoundingMode.HALF_UP);
            return new KnowledgePointMetric(rs.getLong("id"), rs.getString("title"), rate,
                    rs.getLong("evaluated_items"), rate.compareTo(BigDecimal.valueOf(60)) < 0);
        }, args(courseId, classId));
    }

    private List<FrequentQuestion> frequentQuestions(Long courseId, Long classId) {
        String sql = "SELECT SUBSTRING(m.content,1,160) excerpt,COUNT(*) total "
                + "FROM conversation_message m JOIN conversation c ON c.id=m.conversation_id "
                + (classId == null ? "" : "JOIN course_members cm ON cm.course_id=m.course_id "
                        + "AND cm.user_id=c.owner_id AND cm.status='ACTIVE' ")
                + "WHERE m.course_id=? AND m.role='USER' AND m.status='COMPLETE'"
                + (classId == null ? "" : " AND cm.class_id=?")
                + " GROUP BY SUBSTRING(m.content,1,160) ORDER BY total DESC LIMIT 10";
        return jdbc.query(sql, (rs, row) -> new FrequentQuestion(
                rs.getString("excerpt"), rs.getLong("total")), args(courseId, classId));
    }

    private TutorMetrics tutor(Long courseId, Long classId) {
        String base = " FROM tutor_interaction ti "
                + (classId == null ? "" : "JOIN course_members cm ON cm.course_id=ti.course_id "
                        + "AND cm.user_id=ti.user_id AND cm.status='ACTIVE' ")
                + "WHERE ti.course_id=?" + (classId == null ? "" : " AND cm.class_id=?");
        long total = count("SELECT COUNT(*)" + base, args(courseId, classId));
        long failed = count("SELECT COUNT(*)" + base + " AND ti.status='FAILED'", args(courseId, classId));
        Map<String, Long> operations = new LinkedHashMap<>();
        jdbc.query("SELECT ti.operation,COUNT(*) total" + base
                        + " GROUP BY ti.operation ORDER BY total DESC",
                (rs, row) -> Map.entry(rs.getString("operation"), rs.getLong("total")),
                args(courseId, classId)).forEach(entry -> operations.put(entry.getKey(), entry.getValue()));
        return new TutorMetrics(total, failed, operations);
    }

    private QaFeedbackMetrics qaFeedback(Long courseId, Long classId) {
        String base = " FROM answer_feedback af "
                + (classId == null ? "" : "JOIN course_members cm ON cm.course_id=af.course_id "
                        + "AND cm.user_id=af.user_id AND cm.status='ACTIVE' ")
                + "WHERE af.course_id=?" + (classId == null ? "" : " AND cm.class_id=?");
        long helpful = count("SELECT COUNT(*)" + base + " AND af.rating='HELPFUL'", args(courseId, classId));
        long notHelpful = count("SELECT COUNT(*)" + base + " AND af.rating='NOT_HELPFUL'",
                args(courseId, classId));
        long total = helpful + notHelpful;
        BigDecimal rate = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(helpful)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new QaFeedbackMetrics(helpful, notHelpful, rate);
    }

    private ErrorMetrics errors(Long courseId, Long classId) {
        String tutorSql = "SELECT COUNT(*) FROM tutor_interaction ti "
                + (classId == null ? "" : "JOIN course_members cm ON cm.course_id=ti.course_id "
                        + "AND cm.user_id=ti.user_id AND cm.status='ACTIVE' ")
                + "WHERE ti.course_id=? AND ti.status='FAILED'"
                + (classId == null ? "" : " AND cm.class_id=?");
        long tutorFailures = count(tutorSql, args(courseId, classId));
        String reviewSql = "SELECT COUNT(*) FROM review_job r "
                + (classId == null ? "" : "LEFT JOIN submission s ON s.id=r.submission_id "
                        + "AND s.course_id=r.course_id "
                        + "LEFT JOIN assignment a ON a.id=r.assignment_id "
                        + "AND a.course_id=r.course_id ")
                + "WHERE r.course_id=? AND r.status='FAILED'"
                + (classId == null ? "" : " AND (s.class_id=? OR (r.submission_id IS NULL "
                        + "AND r.assignment_id IS NOT NULL "
                        + "AND (a.class_id IS NULL OR a.class_id=?)))");
        Object[] reviewArgs = classId == null ? new Object[]{courseId}
                : new Object[]{courseId, classId, classId};
        long reviewFailures = count(reviewSql, reviewArgs);
        return new ErrorMetrics(tutorFailures, reviewFailures);
    }

    private void requireClass(Long courseId, Long classId) {
        if (classId == null) return;
        long count = count("SELECT COUNT(*) FROM course_classes WHERE id=? AND course_id=?",
                classId, courseId);
        if (count == 0) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course class not found");
    }

    private String assignmentClause(Long classId) {
        return classId == null ? "" : " AND (a.class_id IS NULL OR a.class_id=?)";
    }

    private String submissionClassClause(Long classId) {
        return classId == null ? "" : " AND s.class_id=? AND (a.class_id IS NULL OR a.class_id=?)";
    }

    private String classClause(String column, Long classId) {
        return classId == null ? "" : " AND " + column + "=?";
    }

    private Object[] args(Long courseId, Long classId) {
        return classId == null ? new Object[]{courseId} : new Object[]{courseId, classId};
    }

    private Object[] submissionArgs(Long courseId, Long classId) {
        return classId == null ? new Object[]{courseId}
                : new Object[]{courseId, classId, classId};
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
