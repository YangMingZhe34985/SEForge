package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.domain.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ObjectiveScorer {
    private final ObjectMapper json;
    public ObjectiveScorer(ObjectMapper json) { this.json = json; }
    public BigDecimal score(AssignmentQuestion question, SubmissionAnswer answer) {
        if (!question.getQuestionType().objective()) throw QuestionContent.invalid("RULE only supports objective questions");
        QuestionContent.validateStandard(question);
        JsonNode standard = QuestionContent.standard(question);
        JsonNode actual;
        try { actual = answer == null ? json.nullNode() : answer.getAnswerText() != null
                ? json.getNodeFactory().textNode(answer.getAnswerText())
                : json.readTree(answer.getAnswerDataJson() == null ? "null" : answer.getAnswerDataJson()); }
        catch (Exception e) { return BigDecimal.ZERO; }
        boolean correct = standard.equals(actual);
        if (question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
            correct = actual.isArray() && standard.isArray() && distinct(actual) && set(actual).equals(set(standard));
        }
        return correct ? question.getMaxScore() : BigDecimal.ZERO;
    }
    private boolean distinct(JsonNode value) { return set(value).size() == value.size(); }
    private Set<JsonNode> set(JsonNode value) { Set<JsonNode> result = new HashSet<>(); value.forEach(result::add); return result; }
}
