package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "rubric_item")
public class RubricItem extends BaseEntity {
    @Column(name = "rubric_id", nullable = false)
    private Long rubricId;
    @Column(name = "question_id")
    private Long questionId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "max_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxScore;
    @Column(columnDefinition = "json")
    private String criteria;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected RubricItem() {
    }

    public RubricItem(Long rubricId, Long questionId, String title, String description,
                      BigDecimal maxScore, String criteriaJson, int sortOrder) {
        this.rubricId = rubricId;
        this.questionId = questionId;
        this.title = title;
        this.description = description;
        this.maxScore = maxScore;
        this.criteria = criteriaJson;
        this.sortOrder = sortOrder;
    }

    public void update(Long questionId, String title, String description, BigDecimal maxScore,
                       String criteriaJson, int sortOrder) {
        this.questionId = questionId;
        this.title = title;
        this.description = description;
        this.maxScore = maxScore;
        this.criteria = criteriaJson;
        this.sortOrder = sortOrder;
    }

    public Long getRubricId() { return rubricId; }
    public Long getQuestionId() { return questionId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getMaxScore() { return maxScore; }
    public String getCriteriaJson() { return criteria; }
    public int getSortOrder() { return sortOrder; }
}
