package com.ustb.seforge.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ustb.seforge.analytics.service.AnalyticsProjectionService;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Invoked only after the owner/course and generation checks under the conversation row lock. */
@Service
public class ConversationErasureService {
    private final JdbcTemplate jdbc;
    private final AnalyticsProjectionService analytics;
    private final ObjectMapper json;
    public ConversationErasureService(JdbcTemplate jdbc,AnalyticsProjectionService analytics,ObjectMapper json){this.jdbc=jdbc;this.analytics=analytics;this.json=json;}
    @Transactional
    public void erase(Long course,Long conversation){
        var excerpts=new HashSet<String>();
        // SQL bounds the fetched prefix by code points; projection keys use Java UTF-16 length.
        // Apply the same truncation as AnalyticsProjectionService, including emoji-containing text.
        jdbc.queryForList("SELECT LEFT(content,160) FROM conversation_message WHERE conversation_id=? AND course_id=? AND role='USER'",String.class,conversation,course)
                .forEach(value->{
                    String prefix=value.substring(0,Math.min(160,value.length()));excerpts.add(prefix);
                    // A legacy projection may have cut a surrogate pair; JDBC's UTF-8 encoder
                    // replaces that lone surrogate when persisting dimension_key.
                    excerpts.add(new String(prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8));
                });
        jdbc.update("DELETE FROM conversation_generation WHERE conversation_id=?",conversation);
        // Explicit deletes fire change triggers; MySQL FK cascades alone do not.
        jdbc.update("DELETE f FROM answer_feedback f JOIN conversation_message m ON m.id=f.message_id WHERE m.conversation_id=? AND m.course_id=?",conversation,course);
        jdbc.update("DELETE c FROM message_citation c JOIN conversation_message m ON m.id=c.message_id WHERE m.conversation_id=? AND m.course_id=?",conversation,course);
        jdbc.update("DELETE FROM conversation_message WHERE conversation_id=? AND course_id=?",conversation,course);
        jdbc.update("DELETE FROM conversation WHERE id=? AND course_id=?",conversation,course);
        analytics.refresh(course);
        jdbc.update("DELETE FROM analytics_total WHERE course_id=? AND metric='question' AND amount<=0",course);
        // Historical aggregate snapshots must not retain an erased question's text either.
        for(var row:jdbc.queryForList("SELECT id,payload FROM analytics_snapshot WHERE course_id=? FOR UPDATE",course)){
            try{
                var tree=(ObjectNode)json.readTree(row.get("payload").toString());var original=tree.path("frequentQuestions");
                if(!original.isArray())continue;
                var filtered=json.createArrayNode();original.forEach(item->{if(!excerpts.contains(item.path("excerpt").asText()))filtered.add(item);});
                if(filtered.size()!=original.size()){
                    tree.set("frequentQuestions",filtered);
                    jdbc.update("UPDATE analytics_snapshot SET payload=? WHERE id=? AND course_id=?",json.writeValueAsString(tree),row.get("id"),course);
                }
            }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot safely redact analytics snapshot",e);}
        }
    }
}
