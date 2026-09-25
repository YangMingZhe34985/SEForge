package com.ustb.seforge.analytics.service;

import static org.assertj.core.api.Assertions.*;
import com.ustb.seforge.common.exception.AppException;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker=true)
class AnalyticsProjectionIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4.4")
            .withCommand("--log-bin-trust-function-creators=1").withDatabaseName("analytics")
            .withUsername("analytics").withPassword("analytics-test-only-password");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");r.add("spring.flyway.enabled",()->true);r.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalyticsProjectionService projection;
    @Autowired AnalyticsService analytics;

    @Test void differentialUpdatesDeletionReplayScopeAndLateCommit() throws Exception {
        for(int id=1;id<=3;id++) jdbc.update("INSERT INTO users(id,email,username,password_hash) VALUES(?,?,?,'test-only')",id,"analytics"+id+"@example.invalid","analytics"+id);
        jdbc.update("INSERT INTO semesters(id,code,name,starts_on,ends_on,status) VALUES(1,'a','a','2026-01-01','2026-12-31','ACTIVE')");
        for(int id=1;id<=2;id++) jdbc.update("INSERT INTO courses(id,code,name,semester_id,owner_id) VALUES(?,?,?,1,1)",id,"a"+id,"Analytics"+id);
        for(int id=11;id<=13;id++) jdbc.update("INSERT INTO course_classes(id,course_id,code,name) VALUES(?,?,?,?)",id,id==13?2:1,"c"+id,"Class"+id);
        jdbc.update("INSERT INTO course_members(course_id,user_id,role) VALUES(1,1,'TEACHER')");
        jdbc.update("INSERT INTO course_members(course_id,class_id,user_id,role) VALUES(1,11,2,'STUDENT'),(1,12,3,'STUDENT')");
        jdbc.update("INSERT INTO assignment(id,course_id,title,status,created_by) VALUES(1,1,'Assignment','PUBLISHED',1)");
        jdbc.update("INSERT INTO rubric(id,assignment_id,title,total_score) VALUES(1,1,'Rubric',10)");
        jdbc.update("INSERT INTO submission(id,assignment_id,course_id,class_id,user_id,attempt_no,status) VALUES(1,1,1,11,2,1,'SUBMITTED')");
        jdbc.update("INSERT INTO grade(id,submission_id,course_id,student_id,final_score,status) VALUES(1,1,1,2,8,'CONFIRMED')");
        jdbc.update("INSERT INTO knowledge_points(id,course_id,title) VALUES(1,1,'Transactions')");
        jdbc.update("INSERT INTO assignment_question(id,assignment_id,course_id,question_type,prompt,max_score,knowledge_point_id) VALUES(1,1,1,'SHORT_ANSWER','Explain',10,1)");
        jdbc.update("INSERT INTO rubric_item(id,rubric_id,question_id,title,max_score) VALUES(1,1,1,'Criterion',10)");
        jdbc.update("INSERT INTO feedback(grade_id,rubric_item_id,author_id,source,content,final_score) VALUES(1,1,1,'TEACHER','Evidence',8)");
        jdbc.update("INSERT INTO tutor_interaction(id,course_id,assignment_id,user_id,operation,status) VALUES(1,1,1,2,'HINT','FAILED')");
        String cursor=projection.refresh(1L);
        var view=projection.view(1,11L);
        assertThat(view.overview().completionRate()).isEqualByComparingTo("100");
        assertThat(view.overview().averageFinalScore()).isEqualByComparingTo("80");
        assertThat(view.knowledgePoints().getFirst().scoreRate()).isEqualByComparingTo("80");
        assertThat(projection.view(1,12L).overview().completedSubmissions()).isZero();
        assertThat(projection.view(2,null).tutor().total()).isZero();
        assertThat(projection.refresh(1L)).isEqualTo(cursor);
        jdbc.update("INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(1,'tutor_interaction',1),(1,'tutor_interaction',1)");
        projection.refresh(1L);assertThat(projection.view(1,null).tutor().total()).isEqualTo(1);
        jdbc.update("UPDATE tutor_interaction SET status='COMPLETED' WHERE id=1");projection.refresh(1L);
        assertThat(projection.view(1,null).errors().tutorFailures()).isZero();
        // Changes made without incrementing a JPA version are still journaled.
        jdbc.update("UPDATE feedback SET final_score=4 WHERE grade_id=1");projection.refresh(1L);
        assertThat(projection.view(1,null).knowledgePoints().getFirst().weak()).isTrue();
        jdbc.update("INSERT INTO conversation(id,course_id,owner_id,title) VALUES(1,1,2,'Questions')");
        jdbc.update("INSERT INTO conversation_message(id,conversation_id,course_id,role,content,status) VALUES(1,1,1,'USER','What is a transaction?','COMPLETE')");
        jdbc.update("INSERT INTO answer_feedback(message_id,course_id,user_id,rating) VALUES(1,1,2,'HELPFUL')");
        projection.refresh(1L);
        assertThat(projection.view(1,11L).frequentQuestions()).hasSize(1);
        assertThat(projection.view(1,12L).frequentQuestions()).isEmpty();
        assertThat(projection.view(1,11L).qaFeedback().helpfulRate()).isEqualByComparingTo("100");
        jdbc.update("DELETE FROM conversation WHERE id=1");projection.refresh(1L);
        assertThat(projection.view(1,null).frequentQuestions()).isEmpty();
        assertThat(projection.view(1,null).qaFeedback().helpful()).isZero();
        analytics.generate(1L,null,Instant.now());analytics.generate(1L,11L,Instant.now());
        assertThat(analytics.list(1L,null,1L,0,1).items()).hasSize(1);
        assertThatThrownBy(()->analytics.dashboard(1L,null,2L)).isInstanceOf(AppException.class);
        assertThatThrownBy(()->analytics.list(1L,13L,1L,0,10)).isInstanceOf(AppException.class);
        // Parent deletion cascades children without child triggers; structural event retracts facts.
        jdbc.update("DELETE FROM assignment WHERE id=1");projection.refresh(1L);
        assertThat(projection.view(1,null).overview().completedSubmissions()).isZero();
        assertThat(projection.view(1,null).knowledgePoints()).isEmpty();
        assertThat(projection.view(1,null).tutor().total()).isZero();
        analytics.generate(1L,null,Instant.now());assertThat(analytics.list(1L,null,1L,1,1).items()).hasSize(1);
        // A low-ID event commits after a higher-ID event was applied: no high-water skip.
        try(var connection=MYSQL.createConnection("")) {
            connection.setAutoCommit(false);
            connection.createStatement().executeUpdate("INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(1,'tutor_interaction',999)");
            jdbc.update("INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(1,'tutor_interaction',998)");
            projection.refresh(1L);connection.commit();projection.refresh(1L);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_change WHERE course_id=1 AND applied=false",Long.class)).isZero();
        var pool=Executors.newFixedThreadPool(2);
        try {var a=pool.submit(()->analytics.generate(1L,null,Instant.now()));var b=pool.submit(()->analytics.generate(1L,null,Instant.now()));a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);}
        finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT source_cursor,COUNT(*) n FROM analytics_snapshot WHERE course_id=1 AND class_id IS NULL GROUP BY source_cursor HAVING n>1) duplicate_snapshots",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_change WHERE course_id=1 AND applied=true AND applied_revision IS NULL",Long.class)).isZero();
    }
}
