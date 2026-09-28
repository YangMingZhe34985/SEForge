package com.ustb.seforge.course.service;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ChapterEvidenceSelectorTest {
    List<KnowledgePointDraftService.Source> sources(long doc,int count,int chars){return IntStream.range(0,count).mapToObj(i->
            new KnowledgePointDraftService.Source(doc*1000+i,doc,doc,1L,"doc",i+1,null,"x".repeat(chars),"v"+doc+"-"+i)).toList();}
    @Test void includesEveryChunkOfThreeNormalDocuments(){
        var result=ChapterEvidenceSelector.select(List.of(sources(1,8,800),sources(2,7,600),sources(3,5,700)));
        assertThat(result.sources()).hasSize(20);assertThat(result.documents()).isEqualTo(3);assertThat(result.sampled()).isFalse();
    }
    @Test void hugeFirstDocumentCannotStarveLaterDocumentsAndSamplesBeginningAndEnd(){
        var result=ChapterEvidenceSelector.select(List.of(sources(1,100,4000),sources(2,7,600),sources(3,5,700)));
        assertThat(result.sources()).hasSize(40);assertThat(result.sampled()).isTrue();
        assertThat(result.sources().stream().filter(s->s.documentId()==2L)).hasSize(7);
        assertThat(result.sources().stream().filter(s->s.documentId()==3L)).hasSize(5);
        assertThat(result.sources().stream().mapToInt(s->s.quote().length()).sum()).isLessThanOrEqualTo(24000);
        assertThat(result.sources().stream().filter(s->s.documentId()==1L).map(KnowledgePointDraftService.Source::page)).contains(1,100);
        assertThat(result.sources().stream().map(KnowledgePointDraftService.Source::id).distinct()).hasSize(40);
    }
    @Test void rejectsMissingChunksAndTooManyDocumentsInsteadOfSilentlyIgnoringThem(){
        assertThatThrownBy(()->ChapterEvidenceSelector.select(List.of(sources(1,1,100),List.of()))).hasMessageContaining("缺少");
        assertThatThrownBy(()->ChapterEvidenceSelector.select(IntStream.range(0,41).mapToObj(i->sources(i,1,10)).toList())).hasMessageContaining("超过 40");
    }
}
