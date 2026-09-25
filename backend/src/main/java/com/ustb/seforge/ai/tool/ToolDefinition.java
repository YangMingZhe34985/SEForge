package com.ustb.seforge.ai.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Read-only DTO adapter. Both callbacks must use authorized Application Services. */
public record ToolDefinition<I, O>(ToolSpecification specification, String version, Class<I> inputType,
                                   Consumer<ToolContext> authorize,
                                   BiFunction<ToolContext, I, O> read) {
    public ToolDefinition {
        Objects.requireNonNull(specification);
        Objects.requireNonNull(inputType);
        Objects.requireNonNull(authorize);
        Objects.requireNonNull(read);
        if (!specification.name().matches("[A-Za-z0-9_-]{1,64}")
                || version == null || !version.matches("[A-Za-z0-9._-]{1,32}")) {
            throw new IllegalArgumentException("Stable tool name/version required");
        }
    }
}
