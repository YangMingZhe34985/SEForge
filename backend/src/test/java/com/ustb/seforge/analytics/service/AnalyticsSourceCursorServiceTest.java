package com.ustb.seforge.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalyticsSourceCursorServiceTest {
    private JdbcTemplate jdbc;
    private AnalyticsSourceCursorService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=ASSIGNMENT");
        jdbc = new JdbcTemplate(dataSource);
        schema();
        service = new AnalyticsSourceCursorService(jdbc);
    }

    @Test
    void membershipInsertRevisionAndDeleteMoveTheCourseCursor() {
        String empty = service.current(1L);

        jdbc.update("INSERT INTO course_members VALUES (1,0,1,10,'STUDENT','ACTIVE',21)");
        String inserted = service.current(1L);
        jdbc.update("UPDATE course_members SET class_id=22,version=version+1 WHERE id=1");
        String revised = service.current(1L);
        jdbc.update("DELETE FROM course_members WHERE id=1");
        String deleted = service.current(1L);

        assertThat(inserted).isNotEqualTo(empty);
        assertThat(revised).isNotEqualTo(inserted);
        assertThat(deleted).isEqualTo(empty);
    }

    @Test
    void gradeAndFeedbackCorrectionsMoveTheCursorButOtherCoursesDoNot() {
        jdbc.update("INSERT INTO grade VALUES (10,0,1)");
        jdbc.update("INSERT INTO feedback VALUES (20,0,10)");
        String original = service.current(1L);

        jdbc.update("UPDATE grade SET version=version+1 WHERE id=10");
        String gradeCorrected = service.current(1L);
        jdbc.update("UPDATE feedback SET version=version+1 WHERE id=20");
        String feedbackCorrected = service.current(1L);
        jdbc.update("INSERT INTO grade VALUES (11,0,2)");
        String unrelatedCourseChanged = service.current(1L);

        assertThat(gradeCorrected).isNotEqualTo(original);
        assertThat(feedbackCorrected).isNotEqualTo(gradeCorrected);
        assertThat(unrelatedCourseChanged).isEqualTo(feedbackCorrected);
    }

    private void schema() {
        direct("course_members", "user_id BIGINT,role VARCHAR(20),status VARCHAR(20),class_id BIGINT");
        direct("assignment", "class_id BIGINT");
        direct("assignment_question", "assignment_id BIGINT");
        direct("knowledge_points", "title VARCHAR(80)");
        direct("submission", "assignment_id BIGINT");
        direct("grade", "");
        jdbc.execute("CREATE TABLE rubric(id BIGINT PRIMARY KEY,version BIGINT,assignment_id BIGINT)");
        jdbc.execute("CREATE TABLE rubric_item(id BIGINT PRIMARY KEY,version BIGINT,rubric_id BIGINT)");
        jdbc.execute("CREATE TABLE feedback(id BIGINT PRIMARY KEY,version BIGINT,grade_id BIGINT)");
        direct("conversation_message", "conversation_id BIGINT");
        direct("tutor_interaction", "user_id BIGINT");
        direct("answer_feedback", "user_id BIGINT");
        direct("review_job", "status VARCHAR(24)");
    }

    private void direct(String table, String extraColumns) {
        String suffix = extraColumns.isBlank() ? "" : "," + extraColumns;
        jdbc.execute("CREATE TABLE " + table
                + "(id BIGINT PRIMARY KEY,version BIGINT,course_id BIGINT" + suffix + ")");
    }
}
