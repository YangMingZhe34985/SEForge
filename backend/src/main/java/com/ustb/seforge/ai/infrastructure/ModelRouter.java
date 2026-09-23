package com.ustb.seforge.ai.infrastructure;

import com.ustb.seforge.ai.application.ModelCapability;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModelRouter {
    private final ModelRegistry registry;

    public ModelRouter(ModelRegistry registry) {
        this.registry = registry;
    }

    public List<AiModelEndpoint> candidates(ModelCapability capability) {
        return registry.candidates(capability);
    }
}
