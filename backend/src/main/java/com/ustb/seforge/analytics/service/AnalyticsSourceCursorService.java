package com.ustb.seforge.analytics.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a stable change cursor for every source that contributes to a dashboard.
 *
 * <p>The cursor is deliberately course-wide. A change therefore may cause an extra refresh for a
 * class dashboard, but can never leave a class materialization falsely clean. Row count/id totals
 * detect inserts and deletes while the optimistic-lock version total detects revisions. The worker
 * uses the cursor to decide whether the requested scope needs to be recomputed from authoritative
 * rows; dashboard request threads never execute these scans.</p>
 */
@Service
public class AnalyticsSourceCursorService {
    private static final List<Source> SOURCES = List.of(
            direct("course_members", "cm"),
            direct("assignment", "a"),
            direct("assignment_question", "aq"),
            direct("knowledge_points", "kp"),
            direct("submission", "s"),
            direct("grade", "g"),
            indirect("rubric", "r", "JOIN assignment a ON a.id=r.assignment_id", "a.course_id"),
            indirect("rubric_item", "ri",
                    "JOIN rubric r ON r.id=ri.rubric_id "
                            + "JOIN assignment a ON a.id=r.assignment_id", "a.course_id"),
            indirect("feedback", "f", "JOIN grade g ON g.id=f.grade_id", "g.course_id"),
            direct("conversation_message", "m"),
            direct("tutor_interaction", "ti"),
            direct("answer_feedback", "af"),
            direct("review_job", "rj")
    );

    private final JdbcTemplate jdbc;

    public AnalyticsSourceCursorService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public String current(Long courseId) {
        MessageDigest digest = sha256();
        for (Source source : SOURCES) {
            Map<String, Object> values = jdbc.queryForMap(source.sql(), courseId);
            update(digest, source.name());
            update(digest, values.get("row_count"));
            update(digest, values.get("id_sum"));
            update(digest, values.get("max_id"));
            update(digest, values.get("version_sum"));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Source direct(String table, String alias) {
        return indirect(table, alias, "", alias + ".course_id");
    }

    private static Source indirect(String table, String alias, String joins, String courseColumn) {
        String sql = "SELECT COUNT(*) row_count,COALESCE(SUM(" + alias + ".id),0) id_sum,"
                + "COALESCE(MAX(" + alias + ".id),0) max_id,"
                + "COALESCE(SUM(" + alias + ".version),0) version_sum FROM "
                + table + " " + alias + " " + joins + " WHERE " + courseColumn + "=?";
        return new Source(table, sql);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, Object value) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private record Source(String name, String sql) {
    }
}
