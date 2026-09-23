package com.ustb.seforge.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keeps database identifiers lossless for JavaScript clients without stringifying metrics. */
@Configuration
public class JacksonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer longIdentifierSerializer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}
