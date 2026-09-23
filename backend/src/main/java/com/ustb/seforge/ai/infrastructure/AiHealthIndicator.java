package com.ustb.seforge.ai.infrastructure;

import com.ustb.seforge.ai.application.ModelCapability;
import com.ustb.seforge.config.SEForgeProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ai")
public class AiHealthIndicator implements HealthIndicator {
    private final SEForgeProperties properties;
    private final ModelRegistry registry;

    public AiHealthIndicator(SEForgeProperties properties, ModelRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public Health health() {
        if (!properties.getAi().isEnabled()) {
            return Health.status("DEGRADED").withDetail("reason", "AI is disabled").build();
        }
        if (!registry.available(ModelCapability.FAST)) {
            return Health.status("DEGRADED").withDetail("reason", "No chat provider is configured").build();
        }
        return Health.up().withDetail("fast", true)
                .withDetail("reasoning", registry.available(ModelCapability.REASONING))
                .withDetail("coding", registry.available(ModelCapability.CODING)).build();
    }
}
