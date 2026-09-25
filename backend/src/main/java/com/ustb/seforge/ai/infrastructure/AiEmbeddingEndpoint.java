package com.ustb.seforge.ai.infrastructure;

import dev.langchain4j.model.embedding.EmbeddingModel;

public record AiEmbeddingEndpoint(String provider, String model, int dimensions, EmbeddingModel embeddingModel) {}
