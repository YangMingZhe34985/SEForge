package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "assignment_question")
public class AssignmentQuestion extends BaseEntity {
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "knowledge_point_id")
    private Long knowledgePointId;
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;
    @Column(nullable = false, columnDefinition = "longtext")
    private String prompt;
    @Column(columnDefinition = "json")
    private String options;
    @Column(name = "reference_answer", columnDefinition = "longtext")
    private String referenceAnswer;
    @Column(name = "max_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxScore;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(columnDefinition = "json")
    private String config;

    protected AssignmentQuestion() {
    }

    public AssignmentQuestion(Long assignmentId, Long courseId, Long knowledgePointId,
                              QuestionType questionType, String prompt, String optionsJson,
                              String referenceAnswer, BigDecimal maxScore, int sortOrder, String configJson) {
        this.assignmentId = assignmentId;
        this.courseId = courseId;
        this.knowledgePointId = knowledgePointId;
        this.questionType = questionType;
        this.prompt = prompt;
        this.options = optionsJson;
        this.referenceAnswer = referenceAnswer;
        this.maxScore = maxScore;
        this.sortOrder = sortOrder;
        this.config = configJson;
    }

    public void update(Long knowledgePointId, QuestionType questionType, String prompt, String optionsJson,
                       String referenceAnswer, BigDecimal maxScore, int sortOrder, String configJson) {
        this.knowledgePointId = knowledgePointId;
        this.questionType = questionType;
        this.prompt = prompt;
        this.options = optionsJson;
        this.referenceAnswer = referenceAnswer;
        this.maxScore = maxScore;
        this.sortOrder = sortOrder;
        this.config = configJson;
    }

    public Long getAssignmentId() { return assignmentId; }
    public Long getCourseId() { return courseId; }
    public Long getKnowledgePointId() { return knowledgePointId; }
    public QuestionType getQuestionType() { return questionType; }
    public String getPrompt() { return prompt; }
    public String getOptionsJson() { return options; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public BigDecimal getMaxScore() { return maxScore; }
    public int getSortOrder() { return sortOrder; }
    public String getConfigJson() { return config; }
}
