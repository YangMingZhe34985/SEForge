package com.ustb.seforge.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.application.AiRequest;
import com.ustb.seforge.ai.application.AiTraceService;
import com.ustb.seforge.ai.application.AiToolCall;
import com.ustb.seforge.ai.application.ModelCapability;
import com.ustb.seforge.ai.domain.AiTrace;
import com.ustb.seforge.ai.repository.AiTraceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiTraceServiceTest {

    @Test
    void recordsOnlyAuthorizedToolNames() {
        AiTraceRepository repository = mock(AiTraceRepository.class);
        when(repository.save(any(AiTrace.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AiTraceService service = new AiTraceService(repository, new ObjectMapper());
        AiRequest request = new AiRequest(ModelCapability.REASONING, 7L, 9L, "tutor:v1",
                "system", "student question", List.of(
                        AiToolCall.succeeded("AssignmentTool", 12),
                        AiToolCall.succeeded("CourseKnowledgeTool", 35)));

        AiTrace trace = service.begin(request, "fake", "fake-model");

        assertThat(trace.getToolCallsJson()).contains("\"name\":\"AssignmentTool\"")
                .contains("\"status\":\"SUCCEEDED\"")
                .contains("\"latencyMs\":12")
                .contains("\"name\":\"CourseKnowledgeTool\"");
        assertThat(trace.getPromptVersion()).isEqualTo("tutor:v1");
    }
}
