package com.ustb.seforge.ai.application;

public record AiResponse(String text, String provider, String model, Integer inputTokens,
                         Integer outputTokens, Long traceId) {
    public AiResponse(String text, String provider, String model, Integer inputTokens,
                      Integer outputTokens) {
        this(text, provider, model, inputTokens, outputTokens, null);
    }
}
