package com.ustb.seforge.conversation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.analytics.service.AnalyticsProjectionService;
import com.ustb.seforge.conversation.service.ConversationErasureService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ConversationErasureServiceTest {
    @Test void erasureAlsoMatchesLegacyJdbcReplacementAtSplitSurrogateBoundary() throws Exception {
        var db=mock(JdbcTemplate.class);var json=new ObjectMapper();
        String prefix="x"+"😀".repeat(100), stored="x"+"😀".repeat(79)+"?";
        when(db.queryForList("SELECT LEFT(content,160) FROM conversation_message WHERE conversation_id=? AND course_id=? AND role='USER'",String.class,9L,1L)).thenReturn(List.of(prefix));
        when(db.queryForList("SELECT id,payload FROM analytics_snapshot WHERE course_id=? FOR UPDATE",1L))
                .thenReturn(List.of(Map.of("id",5L,"payload",json.writeValueAsString(Map.of("frequentQuestions",List.of(Map.of("excerpt",stored,"count",1)))))));
        new ConversationErasureService(db,mock(AnalyticsProjectionService.class),json).erase(1L,9L);
        verify(db).update("UPDATE analytics_snapshot SET payload=? WHERE id=? AND course_id=?","{\"frequentQuestions\":[]}",5L,1L);
    }
    @Test void erasureMatchesUtf16ProjectionKeysForEmojiQuestions() throws Exception {
        var db=mock(JdbcTemplate.class);var json=new ObjectMapper();
        String prefix="😀".repeat(160), projected=prefix.substring(0,160);
        when(db.queryForList("SELECT LEFT(content,160) FROM conversation_message WHERE conversation_id=? AND course_id=? AND role='USER'",String.class,9L,1L)).thenReturn(List.of(prefix));
        when(db.queryForList("SELECT id,payload FROM analytics_snapshot WHERE course_id=? FOR UPDATE",1L))
                .thenReturn(List.of(Map.of("id",5L,"payload",json.writeValueAsString(Map.of("frequentQuestions",List.of(Map.of("excerpt",projected,"count",1)))))));
        new ConversationErasureService(db,mock(AnalyticsProjectionService.class),json).erase(1L,9L);
        verify(db).update("UPDATE analytics_snapshot SET payload=? WHERE id=? AND course_id=?","{\"frequentQuestions\":[]}",5L,1L);
    }
    @Test void removesPrivateExcerptFromHistoricalSnapshotsWithoutRemovingOtherAggregates() throws Exception {
        var db=mock(JdbcTemplate.class);var analytics=mock(AnalyticsProjectionService.class);var json=new ObjectMapper();
        when(db.queryForList("SELECT LEFT(content,160) FROM conversation_message WHERE conversation_id=? AND course_id=? AND role='USER'",String.class,9L,1L))
                .thenReturn(List.of("private question"));
        when(db.queryForList("SELECT id,payload FROM analytics_snapshot WHERE course_id=? FOR UPDATE",1L))
                .thenReturn(List.of(Map.of("id",5L,"payload","{\"overview\":{\"students\":8},\"frequentQuestions\":[{\"excerpt\":\"private question\",\"count\":2},{\"excerpt\":\"other\",\"count\":1}]}")));
        new ConversationErasureService(db,analytics,json).erase(1L,9L);
        verify(db).update("UPDATE analytics_snapshot SET payload=? WHERE id=? AND course_id=?",
                "{\"overview\":{\"students\":8},\"frequentQuestions\":[{\"excerpt\":\"other\",\"count\":1}]}",5L,1L);
        verify(analytics).refresh(1L);
        verify(db).update("DELETE FROM conversation WHERE id=? AND course_id=?",9L,1L);
    }
    @Test void malformedSnapshotCannotSilentlyReportSuccessfulErasure(){
        var db=mock(JdbcTemplate.class);
        when(db.queryForList("SELECT LEFT(content,160) FROM conversation_message WHERE conversation_id=? AND course_id=? AND role='USER'",String.class,9L,1L)).thenReturn(List.of("private"));
        when(db.queryForList("SELECT id,payload FROM analytics_snapshot WHERE course_id=? FOR UPDATE",1L))
                .thenReturn(List.of(Map.of("id",5L,"payload","invalid JSON")));
        var service=new ConversationErasureService(db,mock(AnalyticsProjectionService.class),new ObjectMapper());
        assertThatThrownBy(()->service.erase(1L,9L)).isInstanceOf(IllegalStateException.class).hasMessage("Cannot safely redact analytics snapshot");
    }
}
