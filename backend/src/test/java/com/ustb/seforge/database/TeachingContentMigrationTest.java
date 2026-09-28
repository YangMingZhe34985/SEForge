package com.ustb.seforge.database;
import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker=true)
class TeachingContentMigrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4.4")
            .withCommand("--log-bin-trust-function-creators=1").withDatabaseName("content_upgrade")
            .withUsername("content_test").withPassword("content-test-only-password");
    @Test void existingDocumentAndResourceIdsAndObjectKeysSurviveForwardMigration(){
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).target("12").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()));
        db.update("INSERT INTO users(id,email,username,password_hash) VALUES(1,'migration@example.invalid','migration','not-a-login')");
        db.update("INSERT INTO semesters(id,code,name,starts_on,ends_on,status) VALUES(1,'M1','Migration','2026-01-01','2026-12-31','ACTIVE')");
        db.update("INSERT INTO courses(id,code,name,semester_id,owner_id) VALUES(1,'M1','Migration',1,1)");
        db.update("INSERT INTO course_chapters(id,course_id,title,sort_order) VALUES(1,1,'Existing',1)");
        db.update("INSERT INTO course_resources(id,course_id,chapter_id,uploader_id,name,resource_type,object_key,content_type,size_bytes,status) VALUES(10,1,1,1,'existing.pdf','DOCUMENT','courses/1/existing','application/pdf',100,'ACTIVE')");
        for(long id:new long[]{20,21,22}) db.update("INSERT INTO knowledge_document(id,course_id,resource_id,chapter_id,uploader_id,original_name,object_key,media_type,size_bytes,checksum,status,parser_version,embedding_version,chunking_version) VALUES(?,1,?,1,1,'existing.pdf',?,'application/pdf',100,?,?,'parser','embedding','chunking')",
                id,id==20?10L:null,id==20?"courses/1/existing":"courses/1/legacy-"+id,"checksum-"+id,id==22?"DELETED":"READY");
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).load().migrate();
        assertThat(db.queryForObject("SELECT resource_id FROM knowledge_document WHERE id=20",Long.class)).isEqualTo(10L);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE resource_id IS NULL",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM knowledge_document d JOIN course_resources r ON r.id=d.resource_id AND r.course_id=d.course_id AND r.object_key=d.object_key AND r.chapter_id=d.chapter_id",Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT r.status FROM course_resources r JOIN knowledge_document d ON d.resource_id=r.id WHERE d.id=22",String.class)).isEqualTo("DELETED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM course_resources",Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM knowledge_points",Integer.class)).isZero();
    }
}
