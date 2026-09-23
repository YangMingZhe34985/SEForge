package com.ustb.seforge.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ustb.seforge.analytics.api.DashboardView;
import com.ustb.seforge.common.exception.AppException;
import java.math.BigDecimal;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardQueryServiceTest {
    private JdbcTemplate jdbc;
    private DashboardQueryService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=ASSIGNMENT");
        jdbc = new JdbcTemplate(dataSource);
        service = new DashboardQueryService(jdbc);
        schema();
    }

    @Test
    void aggregatesCompletionGradesKnowledgeTutorFeedbackAndFailures() {
        jdbc.update("INSERT INTO course_members(id,course_id,class_id,user_id,role,status) "
                + "VALUES (1,1,NULL,10,'STUDENT','ACTIVE')");
        jdbc.update("INSERT INTO course_members(id,course_id,class_id,user_id,role,status) "
                + "VALUES (2,1,NULL,11,'STUDENT','ACTIVE')");
        jdbc.update("INSERT INTO assignment VALUES (100,1,NULL,'PUBLISHED')");
        jdbc.update("INSERT INTO rubric VALUES (150,100,200)");
        jdbc.update("INSERT INTO submission VALUES (200,100,1,NULL,10,'SUBMITTED')");
        jdbc.update("INSERT INTO grade VALUES (300,200,1,'CONFIRMED',80)");
        jdbc.update("INSERT INTO knowledge_points VALUES (400,1,'Transactions')");
        jdbc.update("INSERT INTO assignment_question VALUES (500,100,400)");
        jdbc.update("INSERT INTO rubric_item VALUES (600,500,10)");
        jdbc.update("INSERT INTO feedback VALUES (700,300,600,8)");
        jdbc.update("INSERT INTO conversation VALUES (800,1,10)");
        jdbc.update("INSERT INTO conversation_message VALUES (900,800,1,'USER','What is ACID?','COMPLETE')");
        jdbc.update("INSERT INTO tutor_interaction VALUES (1000,1,10,'HINT','COMPLETED')");
        jdbc.update("INSERT INTO tutor_interaction VALUES (1001,1,10,'EXPLAIN','FAILED')");
        jdbc.update("INSERT INTO answer_feedback VALUES (1100,1,10,'HELPFUL')");
        jdbc.update("INSERT INTO review_job(id,course_id,status) VALUES (1200,1,'FAILED')");

        DashboardView result = service.aggregate(1L, null);

        assertThat(result.overview().students()).isEqualTo(2);
        assertThat(result.overview().assignments()).isEqualTo(1);
        assertThat(result.overview().expectedSubmissions()).isEqualTo(2);
        assertThat(result.overview().completedSubmissions()).isEqualTo(1);
        assertThat(result.overview().completionRate()).isEqualByComparingTo("50.00");
        assertThat(result.overview().averageFinalScore()).isEqualByComparingTo("40.00");
        assertThat(result.gradeDistribution()).singleElement().satisfies(bucket -> {
            assertThat(bucket.bucket()).isEqualTo("0-59");
            assertThat(bucket.count()).isEqualTo(1);
        });
        assertThat(result.knowledgePoints()).singleElement().satisfies(metric -> {
            assertThat(metric.scoreRate()).isEqualByComparingTo(BigDecimal.valueOf(80));
            assertThat(metric.weak()).isFalse();
        });
        assertThat(result.frequentQuestions()).singleElement()
                .extracting(DashboardView.FrequentQuestion::excerpt).isEqualTo("What is ACID?");
        assertThat(result.tutor().total()).isEqualTo(2);
        assertThat(result.tutor().failed()).isEqualTo(1);
        assertThat(result.qaFeedback().helpfulRate()).isEqualByComparingTo("100.00");
        assertThat(result.errors().reviewFailures()).isEqualTo(1);
    }

    @Test
    void countsOnlyStudentsEligibleForClassTargetedAssignments() {
        jdbc.update("INSERT INTO course_classes VALUES (21,1)");
        jdbc.update("INSERT INTO course_classes VALUES (22,1)");
        jdbc.update("INSERT INTO course_members VALUES (1,1,21,10,'STUDENT','ACTIVE')");
        jdbc.update("INSERT INTO course_members VALUES (2,1,22,11,'STUDENT','ACTIVE')");
        jdbc.update("INSERT INTO assignment VALUES (100,1,NULL,'PUBLISHED')");
        jdbc.update("INSERT INTO assignment VALUES (101,1,21,'PUBLISHED')");

        DashboardView course = service.aggregate(1L, null);
        DashboardView firstClass = service.aggregate(1L, 21L);

        assertThat(course.overview().expectedSubmissions()).isEqualTo(3);
        assertThat(firstClass.overview().assignments()).isEqualTo(2);
        assertThat(firstClass.overview().expectedSubmissions()).isEqualTo(2);
    }

    @Test
    void validatesClassScopeWithoutRunningDashboardAggregation() {
        jdbc.update("INSERT INTO course_classes VALUES (21,1)");

        service.validateScope(1L, 21L);
        assertThatThrownBy(() -> service.validateScope(2L, 21L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Course class not found");
    }

    @Test
    void classDashboardExcludesReviewFailuresOwnedByOtherClassesOrNoClass() {
        jdbc.update("INSERT INTO course_classes VALUES (21,1)");
        jdbc.update("INSERT INTO course_classes VALUES (22,1)");
        jdbc.update("INSERT INTO assignment VALUES (100,1,NULL,'PUBLISHED')");
        jdbc.update("INSERT INTO assignment VALUES (101,1,21,'PUBLISHED')");
        jdbc.update("INSERT INTO assignment VALUES (102,1,22,'PUBLISHED')");
        jdbc.update("INSERT INTO submission VALUES (200,100,1,21,10,'SUBMITTED')");
        jdbc.update("INSERT INTO submission VALUES (201,100,1,22,11,'SUBMITTED')");
        jdbc.update("INSERT INTO review_job VALUES (1200,1,100,200,'FAILED')");
        jdbc.update("INSERT INTO review_job VALUES (1201,1,100,201,'FAILED')");
        jdbc.update("INSERT INTO review_job VALUES (1202,1,101,NULL,'FAILED')");
        jdbc.update("INSERT INTO review_job VALUES (1203,1,102,NULL,'FAILED')");
        jdbc.update("INSERT INTO review_job VALUES (1204,1,100,NULL,'FAILED')");
        jdbc.update("INSERT INTO review_job VALUES (1205,1,NULL,NULL,'FAILED')");

        DashboardView course = service.aggregate(1L, null);
        DashboardView firstClass = service.aggregate(1L, 21L);
        DashboardView secondClass = service.aggregate(1L, 22L);

        assertThat(course.errors().reviewFailures()).isEqualTo(6);
        assertThat(firstClass.errors().reviewFailures()).isEqualTo(3);
        assertThat(secondClass.errors().reviewFailures()).isEqualTo(3);
    }

    private void schema() {
        jdbc.execute("CREATE TABLE course_classes(id BIGINT PRIMARY KEY,course_id BIGINT)");
        jdbc.execute("CREATE TABLE course_members(id BIGINT PRIMARY KEY,course_id BIGINT,class_id BIGINT,user_id BIGINT,role VARCHAR(20),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE assignment(id BIGINT PRIMARY KEY,course_id BIGINT,class_id BIGINT,status VARCHAR(24))");
        jdbc.execute("CREATE TABLE submission(id BIGINT PRIMARY KEY,assignment_id BIGINT,course_id BIGINT,class_id BIGINT,user_id BIGINT,status VARCHAR(24))");
        jdbc.execute("CREATE TABLE grade(id BIGINT PRIMARY KEY,submission_id BIGINT,course_id BIGINT,status VARCHAR(24),final_score DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE rubric(id BIGINT PRIMARY KEY,assignment_id BIGINT,total_score DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE knowledge_points(id BIGINT PRIMARY KEY,course_id BIGINT,title VARCHAR(160))");
        jdbc.execute("CREATE TABLE assignment_question(id BIGINT PRIMARY KEY,assignment_id BIGINT,knowledge_point_id BIGINT)");
        jdbc.execute("CREATE TABLE rubric_item(id BIGINT PRIMARY KEY,question_id BIGINT,max_score DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE feedback(id BIGINT PRIMARY KEY,grade_id BIGINT,rubric_item_id BIGINT,final_score DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE conversation(id BIGINT PRIMARY KEY,course_id BIGINT,owner_id BIGINT)");
        jdbc.execute("CREATE TABLE conversation_message(id BIGINT PRIMARY KEY,conversation_id BIGINT,course_id BIGINT,role VARCHAR(24),content CLOB,status VARCHAR(24))");
        jdbc.execute("CREATE TABLE tutor_interaction(id BIGINT PRIMARY KEY,course_id BIGINT,user_id BIGINT,operation VARCHAR(32),status VARCHAR(24))");
        jdbc.execute("CREATE TABLE answer_feedback(id BIGINT PRIMARY KEY,course_id BIGINT,user_id BIGINT,rating VARCHAR(20))");
        jdbc.execute("CREATE TABLE review_job(id BIGINT PRIMARY KEY,course_id BIGINT,"
                + "assignment_id BIGINT,submission_id BIGINT,status VARCHAR(24))");
    }
}
