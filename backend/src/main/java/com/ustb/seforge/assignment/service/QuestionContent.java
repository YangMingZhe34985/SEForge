package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.util.*;

/** Version 1 keeps legacy prompt/options/reference columns intact. IDs, never object keys, cross APIs. */
public final class QuestionContent {
    private static final ObjectMapper JSON = new ObjectMapper();
    private QuestionContent() {}
    public static JsonNode config(AssignmentQuestion question) {
        try { return question.getConfigJson() == null ? JSON.createObjectNode() : JSON.readTree(question.getConfigJson()); }
        catch (Exception e) { throw invalid("Stored question content is invalid"); }
    }
    public static List<Long> ids(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > 12) throw invalid("At most 12 attachment IDs are allowed");
        List<Long> ids = new ArrayList<>();
        for (JsonNode id : node) {
            try {
                long value = Long.parseLong(id.asText());
                if (value <= 0 || ids.contains(value)) throw invalid("Invalid or duplicate attachment ID");
                ids.add(value);
            } catch (NumberFormatException e) { throw invalid("Invalid attachment ID"); }
        }
        return List.copyOf(ids);
    }
    public static JsonNode standard(AssignmentQuestion question) {
        JsonNode configured = config(question).path("answerSpec").path("correct");
        if (!configured.isMissingNode()) return configured;
        String legacy = question.getReferenceAnswer();
        if (legacy == null || legacy.isBlank()) throw invalid("Objective question has no valid standard answer; teacher must repair it");
        try {
            if (question.getQuestionType() == com.ustb.seforge.assignment.domain.QuestionType.SINGLE_CHOICE)
                return JSON.getNodeFactory().textNode(legacy.trim());
            return JSON.readTree(legacy);
        } catch (Exception e) { throw invalid("Objective standard answer must be an option ID, ID array or boolean"); }
    }
    public static Set<String> optionIds(AssignmentQuestion question) {
        try {
            JsonNode structured = config(question).path("choices");
            JsonNode options = structured.isArray() ? structured : JSON.readTree(question.getOptionsJson());
            Set<String> result = new LinkedHashSet<>();
            for (JsonNode option : options) {
                String id = structured.isArray() ? option.path("id").asText() : option.asText();
                if (id.isBlank() || !result.add(id)) throw invalid("Option IDs must be nonblank and unique");
            }
            return result;
        } catch (Exception e) { throw invalid("Invalid choices"); }
    }
    public static void validateStandard(AssignmentQuestion question) {
        if (!question.getQuestionType().objective()) return;
        JsonNode answer = standard(question);
        switch (question.getQuestionType()) {
            case TRUE_FALSE -> { if (!answer.isBoolean()) throw invalid("Boolean standard answer required"); }
            case SINGLE_CHOICE -> { if (!answer.isTextual() || !optionIds(question).contains(answer.asText())) throw invalid("Select one valid correct option ID"); }
            case MULTIPLE_CHOICE -> {
                Set<String> seen = new HashSet<>();
                if (!answer.isArray() || answer.isEmpty()) throw invalid("Select correct option IDs");
                for (JsonNode id : answer) if (!id.isTextual() || !optionIds(question).contains(id.asText()) || !seen.add(id.asText())) throw invalid("Invalid correct option IDs");
            }
            default -> { }
        }
    }
    public static AppException invalid(String message) { return new AppException(ErrorCode.VALIDATION_FAILED, message); }
}
