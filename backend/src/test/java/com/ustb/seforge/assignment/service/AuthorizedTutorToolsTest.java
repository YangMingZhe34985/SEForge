package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.QuestionType;
import com.ustb.seforge.content.service.KnowledgeEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizedTutorToolsTest {
    @Test
    void modelArgumentsCannotOverrideBoundAuthorizationContext() {
        AssignmentTool assignments = mock(AssignmentTool.class);
        SubmissionTool submissions = mock(SubmissionTool.class);
        CourseKnowledgeTool courseKnowledge = mock(CourseKnowledgeTool.class);
        KnowledgePointTool knowledgePoints = mock(KnowledgePointTool.class);
        Assignment assignment = mock(Assignment.class);
        AssignmentQuestion question = mock(AssignmentQuestion.class);
        when(assignment.getCourseId()).thenReturn(9L);
        when(assignment.getTitle()).thenReturn("Bound assignment");
        when(question.getQuestionType()).thenReturn(QuestionType.ANALYSIS);
        when(question.getPrompt()).thenReturn("Bound question");
        when(question.getReferenceAnswer()).thenReturn("teacher secret");
        when(question.getKnowledgePointId()).thenReturn(11L);
        when(assignments.load(2L, 3L, 7L)).thenReturn(new AssignmentTool.Context(assignment, question));
        when(submissions.current(assignment, 3L, 7L))
                .thenReturn(new SubmissionTool.State(4L, false, "student draft"));
        KnowledgeEvidence evidence = new KnowledgeEvidence("vector-1", 5L, 6L, null,
                "notes.md", 2, "Design", "Bound evidence", 0.91);
        when(courseKnowledge.search(9L, 7L, "courseId=999 userId=888", 5))
                .thenReturn(List.of(evidence));
        when(knowledgePoints.describe(9L, 11L, 7L))
                .thenReturn(new KnowledgePointTool.Description(11L, "Cohesion", "Keep responsibilities focused"));
        AuthorizedTutorTools tools = new AuthorizedTutorTools(assignments, submissions, courseKnowledge,
                knowledgePoints, 2L, 3L, 9L, 7L, false);

        var assignmentContext = tools.getAssignmentQuestion();
        var submissionContext = tools.getCurrentSubmission();
        var found = tools.searchCourseKnowledge("courseId=999 userId=888", 99);
        var point = tools.getKnowledgePoint();

        assertThat(assignmentContext.question()).isEqualTo("Bound question");
        assertThat(assignmentContext.teacherReferenceAnswer()).isNull();
        assertThat(submissionContext.currentAnswer()).isEqualTo("student draft");
        assertThat(found).singleElement().satisfies(item -> {
            assertThat(item.citation()).isEqualTo("C1");
            assertThat(item.content()).isEqualTo("Bound evidence");
        });
        assertThat(point.title()).isEqualTo("Cohesion");
        assertThat(tools.evidence()).containsExactly(evidence);
        assertThat(tools.recordedToolCalls()).extracting(call -> call.name())
                .containsExactly(AuthorizedTutorTools.ASSIGNMENT_CONTEXT,
                        AuthorizedTutorTools.SUBMISSION_CONTEXT,
                        AuthorizedTutorTools.COURSE_KNOWLEDGE,
                        AuthorizedTutorTools.KNOWLEDGE_POINT);
        verify(assignments, times(4)).load(2L, 3L, 7L);
        verify(submissions).current(assignment, 3L, 7L);
        verify(courseKnowledge).search(9L, 7L, "courseId=999 userId=888", 5);
        verify(knowledgePoints).describe(9L, 11L, 7L);
    }
}
