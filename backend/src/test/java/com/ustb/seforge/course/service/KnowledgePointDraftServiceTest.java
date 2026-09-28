package com.ustb.seforge.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.application.*;
import com.ustb.seforge.common.exception.*;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgePointDraftServiceTest {
    final AiGateway ai=mock(AiGateway.class);
    final KnowledgePointDraftService service=new KnowledgePointDraftService(null,null,null,null,null,null,ai,null,new ObjectMapper(),null,null);
    final List<KnowledgePointDraftService.Source> sources=List.of(new KnowledgePointDraftService.Source(7L,2L,3L,4L,"test.pdf",1,null,"Evidence","vector"));
    final String valid="{\"points\":[{\"name\":\"Cohesion\",\"description\":\"Related responsibilities\",\"importance\":\"CORE\",\"sourceIds\":[\"7\"]}]}";
    AiResponse response(String text){return new AiResponse(text,"provider","model",1,1,99L);}
    @Test void acceptsRawAndFencedJsonAndPreservesTraceAndCitation(){
        for(String text:List.of(valid,"```json\n"+valid+"\n```")){
            var result=service.parseDraft(1L,4L,5L,sources,response(text));
            assertThat(result.traceId()).isEqualTo(99L);
            assertThat(result.points().getFirst().sourceIds()).containsExactly(7L);
            assertThat(result.points().getFirst().manual()).isFalse();
        }
    }
    @Test void rejectsInvalidStructureForeignSourcesAndTrailingData(){
        for(String text:List.of("", "null", "[]",valid+" {}",valid.replace("\"7\"","\"8\""),valid.replace("\"Cohesion\"","123"),valid.replace("CORE","FAKE"),"{\"points\":[]}")){
            assertThatThrownBy(()->service.parseDraft(1L,4L,5L,sources,response(text)))
                    .isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KNOWLEDGE_POINT_INVALID_OUTPUT));
        }
    }
    @Test void deadlineCancelsRuntimeRatherThanReturningPartialDraft(){
        AiStreamHandle handle=mock(AiStreamHandle.class);
        when(ai.stream(any(),any(Duration.class),any(),any(),any())).thenReturn(handle);
        assertThatThrownBy(()->service.generateResponse(request(),Duration.ofMillis(1)))
                .isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KNOWLEDGE_POINT_TIMEOUT));
        verify(handle).cancel();
    }
    @Test void reportsProviderTimeoutWithoutLeakingRawProviderMessage(){
        doAnswer(call->{call.<Consumer<Throwable>>getArgument(4).accept(new AiUnavailableException("secret-provider-response",new TimeoutException("secret")));return mock(AiStreamHandle.class);})
                .when(ai).stream(any(),any(Duration.class),any(),any(),any());
        assertThatThrownBy(()->service.generateResponse(request(),Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(AppException.class,e->{assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KNOWLEDGE_POINT_TIMEOUT);assertThat(e.getMessage()).doesNotContain("secret");});
    }
    @Test void returnsOnlyCompleteRuntimeResponse(){
        doAnswer(call->{call.<Consumer<String>>getArgument(2).accept("partial");call.<Consumer<AiResponse>>getArgument(3).accept(response(valid));return mock(AiStreamHandle.class);})
                .when(ai).stream(any(),any(Duration.class),any(),any(),any());
        assertThat(service.generateResponse(request(),Duration.ofSeconds(1)).text()).isEqualTo(valid);
    }
    @Test void redisFailureBeforeGenerationCostsNoProviderCall(){
        var access=mock(CourseAccessService.class);
        var chapters=mock(com.ustb.seforge.course.repository.CourseChapterRepository.class);
        var documents=mock(com.ustb.seforge.content.repository.KnowledgeDocumentRepository.class);
        var chunks=mock(com.ustb.seforge.content.repository.KnowledgeChunkRepository.class);
        var doc=mock(com.ustb.seforge.content.domain.KnowledgeDocument.class);
        when(chapters.findById(4L)).thenReturn(Optional.of(new com.ustb.seforge.course.domain.CourseChapter(1L,null,"chapter","",0)));
        when(doc.getStatus()).thenReturn(com.ustb.seforge.content.domain.DocumentStatus.READY);
        when(doc.getChapterId()).thenReturn(4L);when(doc.getId()).thenReturn(2L);when(doc.getEmbeddingVersion()).thenReturn("v1");
        when(documents.findAllByCourseIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(doc));
        var chunk=mock(com.ustb.seforge.content.domain.KnowledgeChunk.class);
        when(chunk.getContent()).thenReturn("evidence");
        when(chunks.findAllByDocumentIdAndEmbeddingVersionOrderByChunkIndexAsc(2L,"v1")).thenReturn(List.of(chunk));
        @SuppressWarnings("unchecked") var redis=(org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate>)mock(org.springframework.beans.factory.ObjectProvider.class);
        var target=new KnowledgePointDraftService(access,chapters,null,documents,chunks,null,ai,null,new ObjectMapper(),redis,null);
        assertThatThrownBy(()->target.generate(1L,4L,5L)).isInstanceOfSatisfying(AppException.class,
                e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KNOWLEDGE_POINT_REDIS_UNAVAILABLE));
        verifyNoInteractions(ai);
    }
    AiRequest request(){return new AiRequest(ModelCapability.REASONING,5L,1L,"knowledge-point:v2","system","evidence");}
    @Test void raisesAttemptTimeoutWithoutChangingGlobalAiDefaults(){
        doAnswer(call->{call.<Consumer<AiResponse>>getArgument(3).accept(response(valid));return mock(AiStreamHandle.class);})
                .when(ai).stream(any(),eq(Duration.ofSeconds(180)),any(),any(),any());
        assertThat(service.generateResponse(request(),Duration.ofSeconds(300)).text()).isEqualTo(valid);
    }
}
