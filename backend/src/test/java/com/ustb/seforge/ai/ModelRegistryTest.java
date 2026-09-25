package com.ustb.seforge.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ustb.seforge.ai.application.ModelCapability;
import com.ustb.seforge.ai.infrastructure.ModelRegistry;
import com.ustb.seforge.config.SEForgeProperties;
import org.junit.jupiter.api.Test;

class ModelRegistryTest {
    @Test
    void invalidProviderConfigurationDoesNotPreventConstruction() {
        SEForgeProperties properties = new SEForgeProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setDeepseekApiKey("stub-only");
        properties.getAi().setFastModel(null);
        properties.getAi().setReasoningModel(null);
        properties.getAi().setCodingModel(null);
        ModelRegistry registry = new ModelRegistry(properties);
        assertThat(registry.available(ModelCapability.FAST)).isFalse();
    }
    @Test
    void disabledAiCreatesNoRemoteClientsAndHasNoCandidates() {
        SEForgeProperties properties = new SEForgeProperties();
        properties.getAi().setEnabled(false);

        ModelRegistry registry = new ModelRegistry(properties);

        assertThat(registry.available(ModelCapability.FAST)).isFalse();
        assertThat(registry.candidates(ModelCapability.REASONING)).isEmpty();
    }

    @Test
    void enabledAiWithoutCredentialsRemainsDegraded() {
        SEForgeProperties properties = new SEForgeProperties();
        properties.getAi().setEnabled(true);

        ModelRegistry registry = new ModelRegistry(properties);

        assertThat(registry.candidates(ModelCapability.FAST)).isEmpty();
    }
}
