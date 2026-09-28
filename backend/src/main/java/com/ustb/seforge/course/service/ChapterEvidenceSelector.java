package com.ustb.seforge.course.service;

import java.util.*;
import com.ustb.seforge.common.exception.*;

/** Bounded, deterministic coverage across documents; never silently drops a whole document. */
final class ChapterEvidenceSelector {
    static final int MAX_SOURCES=40, MAX_CHARACTERS=24_000;
    record Selection(List<KnowledgePointDraftService.Source> sources, int documents, int totalChunks, boolean sampled) {}
    static Selection select(List<List<KnowledgePointDraftService.Source>> documents) {
        if(documents.size()>MAX_SOURCES)throw new AppException(ErrorCode.CONFLICT,"章节 READY 文档超过 40 份，请拆分章节后生成；未调用模型");
        if(documents.stream().anyMatch(List::isEmpty))throw new AppException(ErrorCode.CONFLICT,"READY 文档缺少有效 Chunk，请重建索引后生成；未调用模型");
        int total=documents.stream().mapToInt(List::size).sum();
        if(total==0)return new Selection(List.of(),0,0,false);
        int[] quotas=new int[documents.size()];int assigned=0;
        while(assigned<Math.min(MAX_SOURCES,total))for(int i=0;i<quotas.length&&assigned<MAX_SOURCES;i++){
            if(quotas[i]<documents.get(i).size()){quotas[i]++;assigned++;}
        }
        List<KnowledgePointDraftService.Source> selected=new ArrayList<>();
        for(int i=0;i<quotas.length;i++)for(int j=0;j<quotas[i];j++){
            var all=documents.get(i);
            int index=quotas[i]==1?all.size()/2:(int)((long)j*(all.size()-1)/(quotas[i]-1));
            selected.add(all.get(index));
        }
        // Water-fill text budget: short chunks donate unused space to longer chunks.
        int[] lengths=new int[selected.size()];int remaining=MAX_CHARACTERS;boolean progress=true;
        while(remaining>0&&progress){progress=false;for(int i=0;i<lengths.length&&remaining>0;i++){
            if(lengths[i]<selected.get(i).quote().length()){lengths[i]++;remaining--;progress=true;}
        }}
        boolean sampled=selected.size()<total;
        List<KnowledgePointDraftService.Source> result=new ArrayList<>();
        for(int i=0;i<selected.size();i++){
            var s=selected.get(i);int length=lengths[i];
            if(length<s.quote().length()&&length>0&&Character.isHighSurrogate(s.quote().charAt(length-1)))length--;
            sampled|=length<s.quote().length();
            result.add(new KnowledgePointDraftService.Source(s.id(),s.documentId(),s.resourceId(),s.chapterId(),s.name(),s.page(),s.section(),s.quote().substring(0,length),s.vectorId()));
        }
        return new Selection(List.copyOf(result),documents.size(),total,sampled);
    }
}
