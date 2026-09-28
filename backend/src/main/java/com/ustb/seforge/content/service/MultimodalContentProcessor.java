package com.ustb.seforge.content.service;

/** Image interpretation is fallible auxiliary evidence. Original media is always retained separately. */
public interface MultimodalContentProcessor {
    NormalizedContent normalize(Long userId, Long courseId, Long mediaId, byte[] image, String mimeType);
    record NormalizedContent(Long mediaId, String text, String method, String model, Long traceId,
                             boolean authoritative, String limitations) {}
}
