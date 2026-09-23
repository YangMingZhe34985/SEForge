package com.ustb.seforge.ai.application;

/**
 * Request-scoped conversation context for an AI Service.
 *
 * <p>The persisted database history remains the source of truth. This value is deliberately
 * immutable and is used to seed a short-lived {@code ChatMemoryProvider} for one invocation;
 * it is never used as an in-memory replacement for persisted conversation history.</p>
 */
public record AiMemoryContext(String memoryId, String persistedHistory) {
    private static final int MAX_MEMORY_ID_LENGTH = 200;
    private static final int MAX_HISTORY_LENGTH = 64_000;

    public AiMemoryContext {
        if (memoryId == null || memoryId.isBlank()) {
            throw new IllegalArgumentException("memoryId is required");
        }
        memoryId = memoryId.trim();
        if (memoryId.length() > MAX_MEMORY_ID_LENGTH) {
            throw new IllegalArgumentException("memoryId is too long");
        }
        persistedHistory = persistedHistory == null ? "" : persistedHistory.trim();
        if (persistedHistory.length() > MAX_HISTORY_LENGTH) {
            throw new IllegalArgumentException("persistedHistory is too long");
        }
    }
}
