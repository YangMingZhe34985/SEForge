package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.TextNode;
import com.ustb.seforge.ai.application.AiGateway;
import com.ustb.seforge.ai.application.AiRequest;
import com.ustb.seforge.ai.application.AiResponse;
import com.ustb.seforge.ai.application.AiToolsResponse;
import com.ustb.seforge.ai.application.PromptCatalog;
import com.ustb.seforge.assignment.api.TutorRequest;
import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.TutorInteraction;
import com.ustb.seforge.assignment.domain.TutorOperation;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TutorServiceTest {
    @Test
    void fullSolutionIsDeniedBeforeSubmissionAndDeadlineWithoutCallingAi() {
        AssignmentTool assignmentTool = mock(AssignmentTool.class);
        CourseKnowledgeTool knowledgeTool = mock(CourseKnowledgeTool.class);
        KnowledgePointTool knowledgePointTool = mock(KnowledgePointTool.class);
        SubmissionTool submissionTool = mock(SubmissionTool.class);
        TutorPolicyCodec policyCodec = mock(TutorPolicyCodec.class);
        PromptCatalog prompts = mock(PromptCatalog.class);
        AiGateway ai = mock(AiGateway.class);
        TutorInteractionAuditService audit = mock(TutorInteractionAuditService.class);
        TutorService service = new TutorService(assignmentTool, knowledgeTool, knowledgePointTool,
                submissionTool, policyCodec, prompts, ai, audit);

        Assignment assignment = mock(Assignment.class);
        AssignmentQuestion question = mock(AssignmentQuestion.class);
        TutorInteraction interaction = mock(TutorInteraction.class);
        when(assignment.getCourseId()).thenReturn(9L);
        when(assignment.getDueAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(question.getId()).thenReturn(3L);
        when(assignmentTool.load(2L, 3L, 7L)).thenReturn(new AssignmentTool.Context(assignment, question));
        when(submissionTool.current(assignment, 3L, 7L)).thenReturn(new SubmissionTool.State(null, false, null));
        when(policyCodec.read(nullable(String.class))).thenReturn(TutorPolicy.defaults());
        when(audit.start(9L, 2L, 3L, null, 7L, TutorOperation.FULL_SOLUTION, "draft"))
                .thenReturn(interaction);
        when(interaction.getId()).thenReturn(99L);

        var result = service.ask(2L, 7L,
                new TutorRequest(3L, TutorOperation.FULL_SOLUTION, TextNode.valueOf("draft")));

        assertThat(result.allowed()).isFalse();
        assertThat(result.policyMessage()).contains("提交");
        verify(ai, never()).completeWithTools(any(), any(), any());
        verify(audit).deny(99L, result.policyMessage());
    }

    @Test
    void permittedRequestUsesOneRequestScopedAuthorizedToolObject() {
        AssignmentTool assignmentTool = mock(AssignmentTool.class);
        CourseKnowledgeTool knowledgeTool = mock(CourseKnowledgeTool.class);
        KnowledgePointTool knowledgePointTool = mock(KnowledgePointTool.class);
        SubmissionTool submissionTool = mock(SubmissionTool.class);
        TutorPolicyCodec policyCodec = mock(TutorPolicyCodec.class);
        PromptCatalog prompts = mock(PromptCatalog.class);
        AiGateway ai = mock(AiGateway.class);
        TutorInteractionAuditService audit = mock(TutorInteractionAuditService.class);
        TutorService service = new TutorService(assignmentTool, knowledgeTool, knowledgePointTool,
                submissionTool, policyCodec, prompts, ai, audit);
        Assignment assignment = mock(Assignment.class);
        AssignmentQuestion question = mock(AssignmentQuestion.class);
        TutorInteraction interaction = mock(TutorInteraction.class);
        when(assignment.getCourseId()).thenReturn(9L);
        when(assignment.getDueAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(question.getId()).thenReturn(3L);
        when(assignmentTool.load(2L, 3L, 7L)).thenReturn(new AssignmentTool.Context(assignment, question));
        when(submissionTool.current(assignment, 3L, 7L))
                .thenReturn(new SubmissionTool.State(null, false, "saved draft"));
        when(policyCodec.read(nullable(String.class))).thenReturn(TutorPolicy.defaults());
        when(audit.start(9L, 2L, 3L, null, 7L, TutorOperation.HINT, "draft"))
                .thenReturn(interaction);
        when(interaction.getId()).thenReturn(99L);
        when(prompts.load("tutor", "v1"))
                .thenReturn(new PromptCatalog.PromptTemplate("tutor", "v1", "Tutor system prompt"));
        when(ai.completeWithTools(any(), anySet(), any(Object[].class)))
                .thenReturn(new AiToolsResponse(new AiResponse("Try a smaller step", "fake", "fake", 3, 4, 55L),
                        java.util.List.of()));
        ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
        ArgumentCaptor<Object[]> toolsCaptor = ArgumentCaptor.forClass(Object[].class);

        var result = service.ask(2L, 7L,
                new TutorRequest(3L, TutorOperation.HINT, TextNode.valueOf("draft")));

        assertThat(result.allowed()).isTrue();
        assertThat(result.content()).isEqualTo("Try a smaller step");
        verify(ai).completeWithTools(requestCaptor.capture(), anySet(), toolsCaptor.capture());
        assertThat(toolsCaptor.getValue()).singleElement().isInstanceOf(AuthorizedTutorTools.class);
        assertThat(requestCaptor.getValue().userPrompt())
                .contains("Call all four authorized tools")
                .doesNotContain("teacher secret");
        verify(audit).complete(99L, "Try a smaller step", 55L);
    }
}
