package com.ustb.seforge.database;
import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
@Testcontainers(disabledWithoutDocker=true)
class GradePublicationMigrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4.4").withCommand("--log-bin-trust-function-creators=1").withDatabaseName("grade_upgrade").withUsername("grade_test").withPassword("migration-test-only");
    @Test void historicalPublicGradesAndPendingAttemptsSurviveForwardMigration(){
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).target("15").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()));
        db.update("INSERT INTO users(id,email,username,password_hash) VALUES(1,'grade@example.invalid','grade','not-a-login')");
        db.update("INSERT INTO semesters(id,code,name,starts_on,ends_on,status) VALUES(1,'G','G','2026-01-01','2026-12-31','ACTIVE')");
        db.update("INSERT INTO courses(id,code,name,semester_id,owner_id) VALUES(1,'G','G',1,1)");
        db.update("INSERT INTO assignment(id,course_id,title,status,created_by) VALUES(1,1,'G','PUBLISHED',1)");
        db.update("INSERT INTO submission(id,assignment_id,course_id,user_id,attempt_no,status) VALUES(1,1,1,1,1,'GRADED'),(2,1,1,1,2,'SUBMITTED'),(3,1,1,1,3,'DRAFT')");
        db.update("INSERT INTO grade(id,submission_id,course_id,student_id,grader_id,final_score,status,confirmed_at) VALUES(1,1,1,1,1,7,'CONFIRMED','2026-09-20 00:00:00')");
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).load().migrate();
        assertThat(db.queryForObject("SELECT status FROM grade WHERE id=1",String.class)).isEqualTo("PUBLISHED");
        assertThat(db.queryForObject("SELECT final_score FROM grade WHERE id=1",java.math.BigDecimal.class)).isEqualByComparingTo("7");
        assertThat(db.queryForObject("SELECT published_by FROM grade WHERE id=1",Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT published_at=confirmed_at FROM grade WHERE id=1",Boolean.class)).isTrue();
        assertThat(db.queryForObject("SELECT status FROM grade WHERE submission_id=2",String.class)).isEqualTo("WAITING_REVIEW");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM grade WHERE submission_id=3",Integer.class)).isZero();
    }
}
