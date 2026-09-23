package com.ustb.seforge.content.service;

public record KnowledgeEvidence(String citationId, Long chunkId, Long documentId, Long chapterId, String source,
                                Integer page, String section, String content, double score) {
}
