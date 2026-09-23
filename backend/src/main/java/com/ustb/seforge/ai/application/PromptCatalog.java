package com.ustb.seforge.ai.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptCatalog {
    public PromptTemplate load(String name, String version) {
        String resourceName = "prompts/" + name + "/" + version + ".txt";
        try {
            ClassPathResource resource = new ClassPathResource(resourceName);
            return new PromptTemplate(name, version,
                    resource.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Prompt resource not found: " + resourceName, exception);
        }
    }

    public record PromptTemplate(String name, String version, String text) {
        public String identifier() {
            return name + ":" + version;
        }
    }
}
