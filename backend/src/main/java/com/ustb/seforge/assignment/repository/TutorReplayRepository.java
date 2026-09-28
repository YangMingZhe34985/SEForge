package com.ustb.seforge.assignment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class TutorReplayRepository {
    private final JdbcTemplate jdbc;
    public TutorReplayRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean claim(long user, String key, long assignment, String hash) {
        try {
            jdbc.update("INSERT INTO tutor_request_replay(user_id,request_key,assignment_id,request_hash,status) VALUES(?,?,?,?,'PROCESSING')", user,key,assignment,hash);
            return true;
        } catch (DuplicateKeyException duplicate) { return false; }
    }
    public Entry get(long user, String key) {
        return jdbc.queryForObject("SELECT * FROM tutor_request_replay WHERE user_id=? AND request_key=?",
                (rs,n) -> new Entry(rs.getString("request_hash"),rs.getString("status"),rs.getString("response_json"),rs.getString("error_code"),rs.getString("error_message")),user,key);
    }
    public void complete(long user, String key, String json) {
        jdbc.update("UPDATE tutor_request_replay SET status='COMPLETED',response_json=?,updated_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND request_key=? AND status='PROCESSING'",json,user,key);
    }
    public void fail(long user, String key, String code, String message, String details) {
        jdbc.update("UPDATE tutor_request_replay SET status='FAILED',error_code=?,error_message=?,response_json=?,updated_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND request_key=? AND status='PROCESSING'",code,message,details,user,key);
    }
    public record Entry(String hash,String status,String json,String code,String message) {}
}
