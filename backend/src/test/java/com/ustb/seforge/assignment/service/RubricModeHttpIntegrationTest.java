package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.ustb.seforge.ai.application.*;
import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.domain.*;
import com.ustb.seforge.identity.service.IdentityService;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.review.service.ReviewExecutionService;
import com.ustb.seforge.review.domain.ReviewType;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers(disabledWithoutDocker=true) @DirtiesContext
class RubricModeHttpIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4.4")
            .withCommand("--log-bin-trust-function-creators=1").withDatabaseName("rubric_modes").withUsername("rubric_test").withPassword("isolated-rubric-test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");r.add("spring.flyway.enabled",()->true);r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("seforge.jobs.enabled",()->false);
    }
    @Autowired IdentityService identity; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired AsyncJobService jobs; @Autowired ReviewExecutionService reviews;
    @MockitoBean AiGateway ai;
    @MockitoBean com.ustb.seforge.content.infrastructure.ObjectStorage storage;
    @LocalServerPort int port;
    Session teacher,student,outsider;long course;
    @BeforeEach void setup() throws Exception {
        String suffix=UUID.randomUUID().toString().substring(0,8);
        long t=user("teacher"+suffix,AccountType.TEACHER,null),s=user("student"+suffix,AccountType.STUDENT,"S"+suffix),o=user("outsider"+suffix,AccountType.TEACHER,null);
        jdbc.update("insert into semesters(code,name,starts_on,ends_on,status) values(?,?,'2026-01-01','2026-12-31','ACTIVE')",suffix,suffix);
        long semester=jdbc.queryForObject("select id from semesters where code=?",Long.class,suffix);
        jdbc.update("insert into courses(code,name,semester_id,owner_id) values(?,?,?,?)",suffix,suffix,semester,t);
        course=jdbc.queryForObject("select id from courses where code=?",Long.class,suffix);
        jdbc.update("insert into course_members(course_id,user_id,role) values(?,?,'TEACHER')",course,t);
        jdbc.update("insert into course_members(course_id,user_id,role) values(?,?,'STUDENT')",course,s);
        teacher=login("teacher"+suffix,"TEACHER");student=login("S"+suffix,"STUDENT");outsider=login("outsider"+suffix,"TEACHER");
    }
    @Test void objectiveAndManualWithoutRubricPublishScoreAndConfirmWithoutAi() throws Exception {
        String path=create();
        long single=question(path,"SINGLE_CHOICE",4,Map.of("answerSpec",Map.of("correct","A")));
        long multiple=question(path,"MULTIPLE_CHOICE",6,Map.of("answerSpec",Map.of("correct",List.of("A","B"))));
        long bool=question(path,"TRUE_FALSE",3,Map.of("answerSpec",Map.of("correct",true)));
        long manual=question(path,"DESIGN",7,Map.of("gradingMode","MANUAL"));
        data(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        var studentQuestions=data(send(student,"GET",path,null)).path("questions");
        for(var q:studentQuestions) {
            assertThat(q.path("config").has("answerSpec")).isFalse();
            assertThat(q.path("referenceAnswer").isNull() || q.path("referenceAnswer").isMissingNode()).isTrue();
        }
        var tutorRequest=Map.of("questionId",bool,"action","HINT","requestKey",UUID.randomUUID().toString());
        var tutorFirst=data(send(student,"POST",path+"/tutor",tutorRequest));
        var tutorReplay=data(send(student,"POST",path+"/tutor",tutorRequest));
        assertThat(tutorReplay).isEqualTo(tutorFirst);
        assertThat(jdbc.queryForObject("select count(*) from tutor_request_replay where request_key=?",Integer.class,tutorRequest.get("requestKey"))).isEqualTo(1);
        assertThat(send(outsider,"POST",path+"/tutor",tutorRequest).statusCode()).isEqualTo(403);
        long submission=submit(path,List.of(Map.of("questionId",single,"answer","A"),Map.of("questionId",multiple,"answer",List.of("B","A")),Map.of("questionId",bool,"answer",false),Map.of("questionId",manual,"answer","Teacher grades this")));
        assertThat(jdbc.queryForObject("select count(*) from rubric where assignment_id=?",Integer.class,id(path))).isZero();
        assertThat(jdbc.queryForObject("select rule_suggested_score from grade where submission_id=?",java.math.BigDecimal.class,submission)).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("select count(*) from feedback f join grade g on g.id=f.grade_id where g.submission_id=? and f.source='RULE' and f.question_id is not null",Integer.class,submission)).isEqualTo(3);
        var report=review(submission);
        assertThat(report.path("result").path("questionScores")).hasSize(3);
        assertThat(report.path("result").path("manualQuestionIds").toString()).contains(String.valueOf(manual));
        verifyNoInteractions(ai);
        var rows=List.of(Map.of("questionId",single,"score",4),Map.of("questionId",multiple,"score",6),Map.of("questionId",bool,"score",0),Map.of("questionId",manual,"score",5));
        assertThat(send(outsider,"POST","/api/v1/submissions/"+submission+"/grade/confirm",Map.of("score",15,"rubricItems",rows)).statusCode()).isEqualTo(403);
        assertThat(send(student,"GET","/api/v1/submissions/"+submission+"/grade",null).statusCode()).isEqualTo(404);
        var grade=data(send(teacher,"POST","/api/v1/submissions/"+submission+"/grade/confirm",Map.of("score",15,"rubricItems",rows)));
        assertThat(grade.path("maxScore").asInt()).isEqualTo(20);assertThat(grade.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(send(student,"GET","/api/v1/submissions/"+submission+"/grade",null).statusCode()).isEqualTo(404);
        data(send(teacher,"POST","/api/v1/submissions/"+submission+"/grade/publish",null));
        data(send(student,"GET","/api/v1/submissions/"+submission+"/grade",null));verifyNoInteractions(ai);
        // Pure MANUAL is also publishable and confirmable without any rubric.
        String manualPath=create();long only=question(manualPath,"SHORT_ANSWER",5,Map.of("gradingMode","MANUAL"));
        data(send(teacher,"POST",manualPath+"/transition",Map.of("status","PUBLISHED")));
        long manualSubmission=submit(manualPath,List.of(Map.of("questionId",only,"answer","manual")));
        assertThat(review(manualSubmission).path("model").asText()).isEqualTo("MANUAL");
        data(send(teacher,"POST","/api/v1/submissions/"+manualSubmission+"/grade/confirm",Map.of("score",4,"rubricItems",List.of(Map.of("questionId",only,"score",4)))));
        verifyNoInteractions(ai);
        String rulePath=create();long ruleOnly=question(rulePath,"TRUE_FALSE",3,Map.of("answerSpec",Map.of("correct",true)));
        data(send(teacher,"POST",rulePath+"/transition",Map.of("status","PUBLISHED")));
        long ruleSubmission=submit(rulePath,List.of(Map.of("questionId",ruleOnly,"answer",true)));
        assertThat(jdbc.queryForObject("select status from grade where submission_id=?",String.class,ruleSubmission)).isEqualTo("PENDING_CONFIRMATION");
        assertThat(review(ruleSubmission).path("result").path("totalSuggestedScore").asInt()).isEqualTo(3);
        assertThat(send(teacher,"POST","/api/v1/submissions/"+ruleSubmission+"/grade/confirm",Map.of("score",3,"rubricItems",List.of(Map.of("questionId",only,"score",3)))).statusCode()).isEqualTo(404);
        data(send(teacher,"POST","/api/v1/submissions/"+ruleSubmission+"/grade/confirm",Map.of("score",3,"rubricItems",List.of(Map.of("questionId",ruleOnly,"score",3)))));
        verifyNoInteractions(ai);
    }
    @Test void allocationsMustMatchEachAiQuestionAndConcurrentAddsCannotOverAllocate() throws Exception {
        String path=create();long q=question(path,"SHORT_ANSWER",7,Map.of());
        assertThat(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")).statusCode()).isEqualTo(409);
        data(send(teacher,"PUT",path+"/rubric",Map.of("title","Rubric","totalScore",7,"status","DRAFT")));
        long first=data(send(teacher,"POST",path+"/rubric/items",item(q,3))).path("id").asLong();
        assertThat(send(teacher,"PUT",path+"/rubric",Map.of("title","Rubric","totalScore",3,"status","PUBLISHED")).statusCode()).isEqualTo(400);
        assertThat(send(teacher,"POST",path+"/rubric/items",item(q,5)).statusCode()).isEqualTo(400);
        assertThat(send(teacher,"PUT",path+"/rubric/items/"+first,item(q,8)).statusCode()).isEqualTo(400);
        var one=CompletableFuture.supplyAsync(()->unchecked(teacher,"POST",path+"/rubric/items",item(q,4)));
        var two=CompletableFuture.supplyAsync(()->unchecked(teacher,"POST",path+"/rubric/items",item(q,4)));
        assertThat(List.of(one.join().statusCode(),two.join().statusCode()).stream().filter(s->s>=200&&s<300).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(max_score) from rubric_item where question_id=?",java.math.BigDecimal.class,q)).isEqualByComparingTo("7");
        data(send(teacher,"PUT",path+"/rubric",Map.of("title","Rubric","totalScore",7,"status","PUBLISHED")));
        data(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        String invalid=create();question(invalid,"TRUE_FALSE",3,Map.of());
        assertThat(send(teacher,"POST",invalid+"/transition",Map.of("status","PUBLISHED")).statusCode()).isEqualTo(400);
    }
    @Test void optionalLegacyRuleRubricDoesNotBecomeAPublicationRequirement() throws Exception {
        String path=create();long q=question(path,"TRUE_FALSE",5,Map.of("answerSpec",Map.of("correct",true)));
        data(send(teacher,"PUT",path+"/rubric",Map.of("title","Optional legacy rubric","totalScore",10,"status","DRAFT")));
        data(send(teacher,"POST",path+"/rubric/items",item(q,2)));
        data(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        long submission=submit(path,List.of(Map.of("questionId",q,"answer",true)));
        assertThat(jdbc.queryForObject("select rule_suggested_score from grade where submission_id=?",java.math.BigDecimal.class,submission)).isEqualByComparingTo("5");
        verifyNoInteractions(ai);
    }
    @Test void mixedReviewSendsOnlyAiQuestionsAndKeepsTeacherConfirmation() throws Exception {
        String path=create();long objective=question(path,"SINGLE_CHOICE",4,Map.of("answerSpec",Map.of("correct","A")));
        long subjective=question(path,"SHORT_ANSWER",6,Map.of());long manual=question(path,"ANALYSIS",2,Map.of("gradingMode","MANUAL"));
        data(send(teacher,"PUT",path+"/rubric",Map.of("title","Rubric","totalScore",6,"status","DRAFT")));
        long rubricItem=data(send(teacher,"POST",path+"/rubric/items",item(subjective,6))).path("id").asLong();
        data(send(teacher,"PUT",path+"/rubric",Map.of("title","Rubric","totalScore",6,"status","PUBLISHED")));
        data(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        long submission=submit(path,List.of(Map.of("questionId",objective,"answer","A"),Map.of("questionId",subjective,"answer","AI answer"),Map.of("questionId",manual,"answer","MANUAL private answer")));
        when(ai.complete(any())).thenAnswer(call->{
            AiRequest request=call.getArgument(0);String context=request.userPrompt();
            assertThat(context).contains("AI answer").doesNotContain("MANUAL private answer","SINGLE_CHOICE","RULE private stem");
            assertThat(request.systemPrompt()).contains("简体中文","summary","feedback","只输出规定的 JSON");
            return new AiResponse(json.writeValueAsString(Map.of("summary","仅供教师确认的初评建议","totalSuggestedScore",5,"rubricItems",List.of(Map.of("rubricItemId",rubricItem,"suggestedScore",5,"evidence",List.of("学生已解释核心概念"),"issues",List.of("例证不够充分"),"feedback","建议补充软件工程实例")))),"controlled","controlled",10,10);
        });
        var report=review(submission);assertThat(report.path("result").path("totalSuggestedScore").asInt()).isEqualTo(9);
        assertThat(report.toString()).contains("仅供教师确认的初评建议","建议补充软件工程实例","例证不够充分");
        verify(ai,times(1)).complete(any());
        assertThat(jdbc.queryForObject("select status from grade where submission_id=?",String.class,submission)).isNotEqualTo("CONFIRMED");
        var rows=List.of(Map.of("questionId",objective,"score",4),Map.of("rubricItemId",rubricItem,"score",6),Map.of("questionId",manual,"score",2));
        assertThat(send(teacher,"POST","/api/v1/submissions/"+submission+"/grade/confirm",Map.of("score",12,"rubricItems",rows)).statusCode()).isEqualTo(400);
        data(send(teacher,"POST","/api/v1/submissions/"+submission+"/grade/confirm",Map.of("score",12,"rubricItems",rows,"reason","Teacher checked evidence")));
    }
    @Test void manualFallbackAndPublicationAreIndependentAuthorizedAndIdempotent() throws Exception {
        String path=create();long q=question(path,"SHORT_ANSWER",5,Map.of());
        data(send(teacher,"PUT",path+"/rubric",Map.of("title","Rubric","totalScore",5,"status","DRAFT")));
        long item=data(send(teacher,"POST",path+"/rubric/items",item(q,5))).path("id").asLong();
        data(send(teacher,"PUT",path+"/rubric",Map.of("title","Rubric","totalScore",5,"status","PUBLISHED")));
        data(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        long sid=submit(path,List.of(Map.of("questionId",q,"answer","Student evidence")));
        String grade="/api/v1/submissions/"+sid+"/grade";
        var initial=data(send(teacher,"GET",grade,null));assertThat(initial.path("status").asText()).isEqualTo("WAITING_REVIEW");assertThat(initial.hasNonNull("score")).isFalse();
        when(ai.complete(any())).thenThrow(new IllegalStateException("controlled provider unavailable"));
        assertThatThrownBy(()->review(sid)).hasMessageContaining("controlled provider unavailable");
        clearInvocations(ai);
        var manual=Map.of("score",4,"feedback","Manual evidence","rubricItems",List.of(Map.of("rubricItemId",item,"score",4,"feedback","read original submission")));
        assertThat(send(student,"POST",grade+"/manual-review",manual).statusCode()).isEqualTo(403);
        assertThat(send(outsider,"POST",grade+"/manual-review",manual).statusCode()).isEqualTo(403);
        data(send(teacher,"POST",grade+"/manual-review",manual));
        assertThat(jdbc.queryForObject("select final_score from grade where submission_id=?",java.math.BigDecimal.class,sid)).isNull();
        assertThat(send(teacher,"POST",grade+"/publish",null).statusCode()).isEqualTo(409);
        var detail=data(send(teacher,"GET","/api/v1/submissions/"+sid+"/grading",null));
        assertThat(detail.path("submission").path("answers").get(0).path("answer").asText()).isEqualTo("Student evidence");
        assertThat(send(student,"GET","/api/v1/submissions/"+sid+"/grading",null).statusCode()).isEqualTo(403);
        data(send(teacher,"POST",grade+"/confirm",manual));
        assertThat(send(student,"GET",grade,null).statusCode()).isEqualTo(404);
        assertThat(data(send(student,"GET",path,null)).hasNonNull("score")).isFalse();
        assertThat(data(send(student,"GET","/api/v1/grades?courseId="+course,null)).path("total").asInt()).isZero();
        for(var denied:List.of(student,outsider))assertThat(send(denied,"POST",grade+"/publish",null).statusCode()).isEqualTo(403);
        var one=CompletableFuture.supplyAsync(()->unchecked(teacher,"POST",grade+"/publish",null));
        var two=CompletableFuture.supplyAsync(()->unchecked(teacher,"POST",grade+"/publish",null));
        assertThat(one.join().statusCode()).isEqualTo(200);assertThat(two.join().statusCode()).isEqualTo(200);
        var visible=data(send(student,"GET",grade,null));assertThat(visible.path("status").asText()).isEqualTo("PUBLISHED");assertThat(visible.path("score").asInt()).isEqualTo(4);
        assertThat(visible.path("rubricItems")).allMatch(n->n.path("source").asText().equals("TEACHER"));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action='GRADE_PUBLISHED' and target_id=?",Integer.class,initial.path("id").asLong())).isEqualTo(1);
        assertThat(send(teacher,"POST",grade+"/manual-review",manual).statusCode()).isEqualTo(409);verifyNoInteractions(ai);
    }

    @Test void batchPublishSkipsUnconfirmedAndStudentApisHideReferenceAnswers() throws Exception {
        String path=create();long q=question(path,"TRUE_FALSE",5,Map.of("answerSpec",Map.of("correct",true)));
        data(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        long sid=submit(path,List.of(Map.of("questionId",q,"answer",true)));
        assertThat(data(send(teacher,"GET",path,null)).path("questions").get(0).path("config").path("answerSpec").path("correct").asBoolean()).isTrue();
        assertThat(data(send(student,"GET",path,null)).path("questions").get(0).path("config").has("answerSpec")).isFalse();
        assertThat(data(send(student,"GET",path,null)).path("questions").get(0).hasNonNull("referenceAnswer")).isFalse();
        assertThat(data(send(teacher,"GET",path+"/grades/publication",null)).path("unconfirmed").asInt()).isEqualTo(1);
        data(send(teacher,"POST",path+"/grades/publish",null));
        assertThat(send(student,"GET","/api/v1/submissions/"+sid+"/grade",null).statusCode()).isEqualTo(404);
        data(send(teacher,"POST","/api/v1/submissions/"+sid+"/grade/confirm",Map.of("score",5,"rubricItems",List.of(Map.of("questionId",q,"score",5)))));
        assertThat(data(send(teacher,"GET",path+"/grades/publication",null)).path("publishable").asInt()).isEqualTo(1);
        assertThat(send(outsider,"POST",path+"/grades/publish",null).statusCode()).isEqualTo(403);
        data(send(teacher,"POST",path+"/grades/publish",null));data(send(teacher,"POST",path+"/grades/publish",null));
        assertThat(data(send(student,"GET","/api/v1/submissions/"+sid+"/grade",null)).path("score").asInt()).isEqualTo(5);
        verifyNoInteractions(ai);
    }

    @Test void documentReviewResolvesSubmittedReportOrIndependentArtifactNotCourseKnowledge() throws Exception {
        String path=create();long q=question(path,"DOCUMENT_REPORT",5,Map.of("gradingMode","MANUAL","answerSpec",Map.of("allowedFileTypes",List.of("md"))));
        data(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        long mediaId=data(upload(student,path+"/media?purpose=ANSWER&questionId="+q,"report.md","text/markdown","# Requirements\nEvery login shall be authorized")).path("id").asLong();
        long sid=submit(path,List.of(Map.of("questionId",q,"answer",Map.of("text","Report","assetIds",List.of(mediaId)))));
        when(storage.open(anyString())).thenAnswer(c->new java.io.ByteArrayInputStream("# Requirements\nEvery login shall be authorized".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var dimensions=List.of("completeness","consistency","verifiability","clarity").stream().map(d->Map.of("dimension",d,"score",80,"findings",List.of("evidence"),"suggestions",List.of("clarify"))).toList();
        when(ai.complete(any())).thenReturn(new AiResponse(json.writeValueAsString(Map.of("summary","Document reviewed","dimensions",dimensions,"issues",List.of(),"recommendations",List.of("clarify"))),"controlled","controlled",10,10));
        String endpoint="/api/v1/courses/"+course+"/reviews/documents";
        assertThat(send(teacher,"POST",endpoint,Map.of("documentId",1,"documentKind","SRS")).statusCode()).isEqualTo(400);
        assertThat(send(teacher,"POST",endpoint,Map.of("resourceId",1,"documentKind","SRS")).statusCode()).isEqualTo(400);
        var request=Map.of("submissionId",sid,"questionId",q,"mediaId",mediaId,"documentKind","SRS","idempotencyKey","document-"+sid);
        assertThat(send(outsider,"POST",endpoint,request).statusCode()).isEqualTo(403);
        var job=data(send(teacher,"POST",endpoint,request));
        assertThat(data(send(teacher,"POST",endpoint,request)).path("id")).isEqualTo(job.path("id"));
        long jobId=jdbc.queryForObject("select async_job_id from review_job where id=?",Long.class,job.path("id").asLong());
        reviews.execute(jobs.claim(jobId,"document-test").orElseThrow(),ReviewType.DOCUMENT);
        assertThat(data(send(teacher,"GET","/api/v1/courses/"+course+"/reviews/"+job.path("id").asLong()+"/report",null)).path("summary").asText()).isEqualTo("Document reviewed");
        assertThat(jdbc.queryForObject("select final_score from grade where submission_id=?",java.math.BigDecimal.class,sid)).isNull();
        var artifact=data(upload(teacher,"/api/v1/courses/"+course+"/reviews/artifacts","review.md","text/markdown","# Specification"));
        assertThat(send(student,"POST",endpoint,Map.of("artifactId",artifact.path("id").asLong())).statusCode()).isEqualTo(403);
        data(send(teacher,"POST",endpoint,Map.of("artifactId",artifact.path("id").asLong(),"documentKind","README")));
        assertThat(send(teacher,"POST",endpoint,Map.of("artifactId",artifact.path("id").asLong(),"documentKind","UNSAFE")).statusCode()).isEqualTo(400);
    }
    @Test void tutorHttpErrorsCarryTraceAndConcurrentRetryDoesNotInvokeAiAgain() throws Exception {
        String path=create();long q=question(path,"SHORT_ANSWER",5,Map.of("gradingMode","MANUAL"));
        data(send(teacher,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        String key=UUID.randomUUID().toString();
        var body=Map.of("questionId",q,"action","HINT","draftAnswer","我的思路","requestKey",key);
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        when(ai.completeWithTools(any(),anySet(),any(Object[].class))).thenAnswer(call->{
            entered.countDown();
            if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test release timeout");
            return new AiToolsResponse(new AiResponse("从职责划分开始分析","controlled","controlled",1,1),List.of());
        });
        var first=CompletableFuture.supplyAsync(()->unchecked(student,"POST",path+"/tutor",body));
        try {
            assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var pending=send(student,"POST",path+"/tutor",body);
            assertThat(pending.statusCode()).isEqualTo(409);
            assertThat(json.readTree(pending.body()).path("code").asText()).isEqualTo("TUTOR_PROCESSING");
        } finally {release.countDown();}
        var answer=data(first.get(15,java.util.concurrent.TimeUnit.SECONDS));
        assertThat(data(send(student,"POST",path+"/tutor",body))).isEqualTo(answer);
        verify(ai,times(1)).completeWithTools(any(),anySet(),any(Object[].class));
        var changed=new LinkedHashMap<String,Object>(body);changed.put("draftAnswer","changed");
        assertThat(send(student,"POST",path+"/tutor",changed).statusCode()).isEqualTo(409);
        when(ai.completeWithTools(any(),anySet(),any(Object[].class))).thenThrow(new AiUnavailableException("opaque",new java.net.SocketTimeoutException("SECRET must not leak")));
        changed.put("requestKey",UUID.randomUUID().toString());
        var failed=send(student,"POST",path+"/tutor",changed);
        assertThat(failed.statusCode()).isEqualTo(504);
        var error=json.readTree(failed.body());
        assertThat(error.path("code").asText()).isEqualTo("TUTOR_TIMEOUT");
        assertThat(error.path("traceId").asText()).isNotBlank();
        assertThat(error.path("details").path("reason").asText()).isEqualTo("TIMEOUT");
        assertThat(failed.body()).doesNotContain("SECRET");
        assertThat(send(student,"POST",path+"/tutor",changed).statusCode()).isEqualTo(504);
        verify(ai,times(2)).completeWithTools(any(),anySet(),any(Object[].class));
    }
    private HttpResponse<String> upload(Session s,String path,String name,String type,String body)throws Exception {
        String boundary="seforge-test-boundary";String bytes="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+type+"\r\n\r\n"+body+"\r\n--"+boundary+"--\r\n";
        return s.client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header(s.header,s.token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(bytes)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode review(long submission)throws Exception {
        var job=data(send(teacher,"POST","/api/v1/courses/"+course+"/reviews/assignments",Map.of("submissionId",submission,"idempotencyKey",UUID.randomUUID().toString())));
        long reviewId=job.path("id").asLong();long asyncId=jdbc.queryForObject("select async_job_id from review_job where id=?",Long.class,reviewId);
        reviews.execute(jobs.claim(asyncId,"rubric-test-worker").orElseThrow(),ReviewType.ASSIGNMENT);
        return data(send(teacher,"GET","/api/v1/courses/"+course+"/reviews/"+reviewId+"/report",null));
    }
    private Map<String,Object> item(long question,int max){return Map.of("questionId",question,"title","criterion","maxScore",max,"orderIndex",0);}
    private String create()throws Exception{return "/api/v1/assignments/"+data(send(teacher,"POST","/api/v1/courses/"+course+"/assignments",Map.of("title","Rubric mode test"))).path("id").asLong();}
    private long id(String path){return Long.parseLong(path.substring(path.lastIndexOf('/')+1));}
    private long question(String path,String type,int points,Map<String,Object> config)throws Exception {
        var body=new LinkedHashMap<String,Object>();body.put("type",type);body.put("prompt",type.equals("SINGLE_CHOICE")?"RULE private stem":"Explain cohesion");body.put("points",points);body.put("orderIndex",0);body.put("config",config);
        if(type.endsWith("CHOICE"))body.put("options",List.of("A","B","C","D"));
        return data(send(teacher,"POST",path+"/questions",body)).path("id").asLong();
    }
    private long submit(String path,List<?> answers)throws Exception{return data(send(student,"POST",path+"/submissions",Map.of("submissionKey",UUID.randomUUID().toString(),"answers",answers))).path("id").asLong();}
    private long user(String name,AccountType type,String number){return identity.createUser(new CreateUserRequest(name+"@example.invalid",name,"RubricTestPassword!",name,type,Set.of(GlobalRole.USER),number)).id();}
    private record Session(HttpClient client,String header,String token){}
    private Session login(String name,String portal)throws Exception {var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();var csrf=json.readTree(client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).path("data");var session=new Session(client,csrf.path("headerName").asText(),csrf.path("token").asText());data(send(session,"POST","/api/v1/auth/login",Map.of("identifier",name,"portal",portal,"password","RubricTestPassword!")));csrf=data(send(session,"GET","/api/v1/auth/csrf",null));return new Session(client,csrf.path("headerName").asText(),csrf.path("token").asText());}
    private HttpResponse<String> send(Session s,String method,String path,Object body)throws Exception {return s.client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header(s.header,s.token).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());}
    private HttpResponse<String> unchecked(Session s,String method,String path,Object body){try{return send(s,method,path,body);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode data(HttpResponse<String> r)throws Exception {assertThat(r.statusCode()).as(r.body()).isBetween(200,299);return json.readTree(r.body()).path("data");}
}
