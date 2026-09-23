package com.ustb.seforge.assignment.service;

import com.ustb.seforge.ai.application.AiToolCall;
import com.ustb.seforge.ai.application.AiToolCallRecorder;
import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.content.service.KnowledgeEvidence;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A single-request capability object. The model never receives authorization IDs:
 * every tool invocation is bound to the authenticated user and selected question
 * before it is registered with LangChain4j.
 */
public final class AuthorizedTutorTools implements AiToolCallRecorder {
    public static final String ASSIGNMENT_CONTEXT = "get_assignment_question";
    public static final String SUBMISSION_CONTEXT = "get_current_submission";
    public static final String COURSE_KNOWLEDGE = "search_course_knowledge";
    public static final String KNOWLEDGE_POINT = "get_knowledge_point";

    private final AssignmentTool assignments;
    private final SubmissionTool submissions;
    private final CourseKnowledgeTool courseKnowledge;
    private final KnowledgePointTool knowledgePoints;
    private final Long assignmentId;
    private final Long questionId;
    private final Long courseId;
    private final Long userId;
    private final boolean exposeReferenceAnswer;
    private final List<AiToolCall> calls = new ArrayList<>();
    private final Map<Long, KnowledgeEvidence> evidence = new LinkedHashMap<>();

    public AuthorizedTutorTools(AssignmentTool assignments, SubmissionTool submissions,
                                CourseKnowledgeTool courseKnowledge, KnowledgePointTool knowledgePoints,
                                Long assignmentId, Long questionId, Long courseId, Long userId,
                                boolean exposeReferenceAnswer) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.courseKnowledge = courseKnowledge;
        this.knowledgePoints = knowledgePoints;
        this.assignmentId = assignmentId;
        this.questionId = questionId;
        this.courseId = courseId;
        this.userId = userId;
        this.exposeReferenceAnswer = exposeReferenceAnswer;
    }

    @Tool(name = ASSIGNMENT_CONTEXT,
            value = "Read the authorized assignment and question. No identifiers are accepted.")
    public AssignmentQuestionContext getAssignmentQuestion() {
        return invoke(ASSIGNMENT_CONTEXT, () -> {
            AssignmentTool.Context context = loadBoundContext();
            Assignment assignment = context.assignment();
            AssignmentQuestion question = context.question();
            return new AssignmentQuestionContext(assignment.getTitle(), assignment.getDescription(),
                    question.getQuestionType().name(), question.getPrompt(), question.getOptionsJson(),
                    exposeReferenceAnswer ? question.getReferenceAnswer() : null);
        });
    }

    @Tool(name = SUBMISSION_CONTEXT,
            value = "Read the authenticated student's current answer and submission state. No identifiers are accepted.")
    public SubmissionContext getCurrentSubmission() {
        return invoke(SUBMISSION_CONTEXT, () -> {
            AssignmentTool.Context context = loadBoundContext();
            SubmissionTool.State state = submissions.current(context.assignment(), questionId, userId);
            return new SubmissionContext(state.validSubmission(), state.persistedAnswer());
        });
    }

    @Tool(name = COURSE_KNOWLEDGE,
            value = "Search only the authorized course knowledge base. Cite returned labels such as [C1].")
    public List<CourseEvidence> searchCourseKnowledge(
            @P("A focused search query derived from the current question") String query,
            @P(value = "Maximum results from 1 through 5", required = false) Integer maximumResults) {
        return invoke(COURSE_KNOWLEDGE, () -> {
            AssignmentTool.Context context = loadBoundContext();
            String safeQuery = query == null || query.isBlank() ? context.question().getPrompt() : query.trim();
            if (safeQuery.length() > 2_000) safeQuery = safeQuery.substring(0, 2_000);
            int limit = maximumResults == null ? 5 : Math.min(Math.max(maximumResults, 1), 5);
            List<KnowledgeEvidence> found = courseKnowledge.search(courseId, userId, safeQuery, limit);
            synchronized (evidence) {
                for (KnowledgeEvidence item : found) evidence.putIfAbsent(item.chunkId(), item);
                return found.stream().map(item -> toCourseEvidence(item, citationFor(item))).toList();
            }
        });
    }

    @Tool(name = KNOWLEDGE_POINT,
            value = "Read the knowledge point bound to the authorized question. No identifiers are accepted.")
    public KnowledgePointContext getKnowledgePoint() {
        return invoke(KNOWLEDGE_POINT, () -> {
            AssignmentTool.Context context = loadBoundContext();
            KnowledgePointTool.Description value = knowledgePoints.describe(
                    courseId, context.question().getKnowledgePointId(), userId);
            return value == null ? new KnowledgePointContext(false, null, null)
                    : new KnowledgePointContext(true, value.title(), value.description());
        });
    }

    @Override
    public void beginAiAttempt() {
        synchronized (calls) {
            calls.clear();
        }
        synchronized (evidence) {
            evidence.clear();
        }
    }

    @Override
    public List<AiToolCall> recordedToolCalls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    public List<KnowledgeEvidence> evidence() {
        synchronized (evidence) {
            return List.copyOf(evidence.values());
        }
    }

    private String citationFor(KnowledgeEvidence item) {
        int index = 1;
        for (Long chunkId : evidence.keySet()) {
            if (java.util.Objects.equals(chunkId, item.chunkId())) return "C" + index;
            index++;
        }
        throw new IllegalStateException("Evidence was not registered");
    }

    private AssignmentTool.Context loadBoundContext() {
        AssignmentTool.Context context = assignments.load(assignmentId, questionId, userId);
        if (!courseId.equals(context.assignment().getCourseId())) {
            throw new SecurityException("Authorized course context changed");
        }
        return context;
    }

    private CourseEvidence toCourseEvidence(KnowledgeEvidence item, String citation) {
        return new CourseEvidence(citation, item.source(), item.page(), item.section(), item.content(), item.score());
    }

    private <T> T invoke(String name, Supplier<T> invocation) {
        long startedAt = System.nanoTime();
        try {
            T result = invocation.get();
            addCall(AiToolCall.succeeded(name, elapsedMillis(startedAt)));
            return result;
        } catch (RuntimeException failure) {
            addCall(AiToolCall.failed(name, elapsedMillis(startedAt), failure));
            throw failure;
        }
    }

    private void addCall(AiToolCall call) {
        synchronized (calls) {
            calls.add(call);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    public record AssignmentQuestionContext(String assignmentTitle, String assignmentDescription,
                                            String questionType, String question, String optionsJson,
                                            String teacherReferenceAnswer) {
    }

    public record SubmissionContext(boolean submitted, String currentAnswer) {
    }

    public record KnowledgePointContext(boolean available, String title, String description) {
    }

    public record CourseEvidence(String citation, String source, Integer page, String section,
                                 String content, double score) {
    }
}
