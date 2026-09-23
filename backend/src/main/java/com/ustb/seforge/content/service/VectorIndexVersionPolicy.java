package com.ustb.seforge.content.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.content.infrastructure.EmbeddingProvider;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Resolves the read (active/blue) and ingestion (write/green) index versions.
 * The values are intentionally configuration driven so every API/worker replica
 * switches together on deployment instead of maintaining unsafe process-local state.
 */
@Service
public class VectorIndexVersionPolicy {
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final SEForgeProperties properties;
    private final EmbeddingProvider embeddings;

    public VectorIndexVersionPolicy(SEForgeProperties properties, EmbeddingProvider embeddings) {
        this.properties = properties;
        this.embeddings = embeddings;
    }

    public String activeVersion() {
        return configuredOrProvider(properties.getVectorStore().getActiveVersion());
    }

    public String writeVersion() {
        String configured = properties.getVectorStore().getWriteVersion();
        return configured == null || configured.isBlank() ? activeVersion() : validate(configured);
    }

    public Set<String> managedVersions() {
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        versions.add(activeVersion());
        versions.add(writeVersion());
        return Set.copyOf(versions);
    }

    public String requireManaged(String requested) {
        String version = requested == null || requested.isBlank() ? writeVersion() : validate(requested);
        if (!managedVersions().contains(version)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Index version is not configured as active-version or write-version");
        }
        return version;
    }

    public void requireWritable(String version) {
        if (!writeVersion().equals(validate(version))) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Only the configured write-version can be repaired or reindexed");
        }
    }

    public String validate(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!VERSION.matcher(normalized).matches()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Embedding version must match " + VERSION.pattern());
        }
        return normalized;
    }

    private String configuredOrProvider(String configured) {
        String value = configured == null || configured.isBlank() ? embeddings.version() : configured;
        return validate(value);
    }
}
