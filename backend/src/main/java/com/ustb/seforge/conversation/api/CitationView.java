package com.ustb.seforge.conversation.api;

import com.ustb.seforge.content.service.KnowledgeEvidence;
import com.ustb.seforge.conversation.domain.MessageCitation;

public record CitationView(String id, Long documentId, Long chunkId, String label, String source,
                           Integer page, String chapter, String section, String quote, Double score) {
    public static CitationView from(KnowledgeEvidence value, int ordinal) {
        return new CitationView(value.citationId(), value.documentId(), value.chunkId(), "C" + ordinal,
                value.source(), value.page(), value.chapterId() == null ? null : value.chapterId().toString(),
                value.section(), abbreviate(value.content()), value.score());
    }

    public static CitationView from(MessageCitation value) {
        return new CitationView(value.getId().toString(), value.getDocumentId(), value.getChunkId(),
                value.getLabel(), value.getSourceTitle(), value.getPage(), value.getChapter(), value.getSection(),
                value.getQuote(), value.getRelevanceScore());
    }

    private static String abbreviate(String content) {
        return content.length() <= 500 ? content : content.substring(0, 500) + "…";
    }
}
