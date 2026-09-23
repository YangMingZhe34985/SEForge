package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "rubric")
public class Rubric extends BaseEntity {
    @Column(name = "assignment_id", nullable = false, unique = true)
    private Long assignmentId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(name = "total_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalScore;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RubricStatus status = RubricStatus.DRAFT;

    protected Rubric() {
    }

    public Rubric(Long assignmentId, String title, BigDecimal totalScore) {
        this.assignmentId = assignmentId;
        this.title = title;
        this.totalScore = totalScore;
    }

    public void update(String title, BigDecimal totalScore, RubricStatus status) {
        this.title = title;
        this.totalScore = totalScore;
        if (status != null) this.status = status;
    }

    public void synchronizeTotalScore(BigDecimal itemTotal) {
        this.totalScore = itemTotal;
    }

    public Long getAssignmentId() { return assignmentId; }
    public String getTitle() { return title; }
    public BigDecimal getTotalScore() { return totalScore; }
    public RubricStatus getStatus() { return status; }
}
