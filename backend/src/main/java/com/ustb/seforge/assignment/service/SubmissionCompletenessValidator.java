package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.QuestionType;
import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SubmissionCompletenessValidator {
    private final ObjectMapper objectMapper;

    public SubmissionCompletenessValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void requireComplete(List<AssignmentQuestion> questions, List<SubmissionAnswer> answers) {
        Map<Long, String> issues = findIssues(questions, answers);
        if (!issues.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Every assignment question requires a valid answer",
                    Map.of("questions", issues));
        }
    }

    public boolean isComplete(List<AssignmentQuestion> questions, List<SubmissionAnswer> answers) {
        return findIssues(questions, answers).isEmpty();
    }

    private Map<Long, String> findIssues(List<AssignmentQuestion> questions, List<SubmissionAnswer> answers) {
        if (questions == null || questions.isEmpty()) {
            return Map.of(0L, "Assignment has no questions");
        }
        Map<Long, SubmissionAnswer> answerByQuestion = new HashMap<>();
        if (answers != null) {
            for (SubmissionAnswer answer : answers) {
                if (answer != null && answer.getQuestionId() != null) {
                    answerByQuestion.putIfAbsent(answer.getQuestionId(), answer);
                }
            }
        }

        Map<Long, String> issues = new LinkedHashMap<>();
        for (AssignmentQuestion question : questions) {
            SubmissionAnswer answer = answerByQuestion.get(question.getId());
            String issue = validate(question, answer);
            if (issue != null) issues.put(question.getId(), issue);
        }
        return issues;
    }

    private String validate(AssignmentQuestion question, SubmissionAnswer answer) {
        if (answer == null) return "Answer is missing";
        QuestionType type = question.getQuestionType();
        if (type == null) return "Question type is invalid";
        JsonNode value = answerValue(answer);
        return switch (type) {
            case SINGLE_CHOICE -> validateSingleChoice(question, value);
            case MULTIPLE_CHOICE -> validateMultipleChoice(question, value);
            case TRUE_FALSE -> value.isBoolean() ? null : "A boolean answer is required";
            case SHORT_ANSWER, ANALYSIS, DESIGN -> nonBlankText(value)
                    ? null : "A non-blank text answer is required";
            case CODE -> nonBlankText(value) || nonBlank(answer.getAttachmentObjectKey())
                    ? null : "Code text or a source attachment is required";
        };
    }

    private String validateSingleChoice(AssignmentQuestion question, JsonNode value) {
        if (!nonBlankText(value)) return "A choice is required";
        Set<String> options = configuredOptions(question);
        return !options.isEmpty() && options.contains(value.textValue())
                ? null : "The selected option is not valid";
    }

    private String validateMultipleChoice(AssignmentQuestion question, JsonNode value) {
        if (!value.isArray() || value.isEmpty()) return "At least one choice is required";
        Set<String> options = configuredOptions(question);
        if (options.isEmpty()) return "The configured choices are invalid";
        List<String> selected = new ArrayList<>();
        for (JsonNode item : value) {
            if (!nonBlankText(item) || !options.contains(item.textValue())) {
                return "One or more selected options are not valid";
            }
            selected.add(item.textValue());
        }
        return new HashSet<>(selected).size() == selected.size()
                ? null : "Duplicate choices are not allowed";
    }

    private Set<String> configuredOptions(AssignmentQuestion question) {
        JsonNode configured = parse(question.getOptionsJson());
        if (!configured.isArray()) return Set.of();
        Set<String> values = new HashSet<>();
        for (JsonNode option : configured) {
            if (!nonBlankText(option)) return Set.of();
            values.add(option.textValue());
        }
        return values;
    }

    private JsonNode answerValue(SubmissionAnswer answer) {
        if (answer.getAnswerText() != null) return TextNode.valueOf(answer.getAnswerText());
        return parse(answer.getAnswerDataJson());
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) return NullNode.getInstance();
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ignored) {
            return NullNode.getInstance();
        }
    }

    private boolean nonBlankText(JsonNode value) {
        return value != null && value.isTextual() && nonBlank(value.textValue());
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
