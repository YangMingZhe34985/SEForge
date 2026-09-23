package com.ustb.seforge.content.support;

import com.ustb.seforge.content.infrastructure.EmbeddingProvider;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class FakeEmbeddingProvider implements EmbeddingProvider {
    @Override
    public String version() { return "fake-v1"; }

    @Override
    public List<Embedding> embedAll(String embeddingVersion, List<TextSegment> segments) {
        return segments.stream().map(segment -> embed(embeddingVersion, segment.text())).toList();
    }

    @Override
    public Embedding embed(String embeddingVersion, String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((embeddingVersion + "\u0000" + text).getBytes(StandardCharsets.UTF_8));
            float[] vector = new float[16];
            for (int index = 0; index < vector.length; index++) vector[index] = digest[index] / 128f;
            Embedding embedding = Embedding.from(vector);
            embedding.normalize();
            return embedding;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
