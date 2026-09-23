package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "feedback")
public class Feedback extends BaseEntity {
    @Column(name = "grade_id", nullable = false)
    private Long gradeId;
    @Column(name = "rubric_item_id")
    private Long rubricItemId;
    @Column(name = "author_id")
    private Long authorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FeedbackSource source;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "suggested_score", precision = 10, scale = 2)
    private BigDecimal suggestedScore;
    @Column(name = "final_score", precision = 10, scale = 2)
    private BigDecimal finalScore;
    @Column(columnDefinition = "json")
    private String evidence;
    @Column(name = "issue_codes", columnDefinition = "json")
    private String issueCodes;

    protected Feedback() {
    }

    public Feedback(Long gradeId, Long rubricItemId, Long authorId, FeedbackSource source,
                    String content, BigDecimal suggestedScore, String evidenceJson, String issueCodesJson) {
        this.gradeId = gradeId;
        this.rubricItemId = rubricItemId;
        this.authorId = authorId;
        this.source = source;
        this.content = content;
        this.suggestedScore = suggestedScore;
        this.evidence = evidenceJson;
        this.issueCodes = issueCodesJson;
    }

    public static Feedback ai(Long gradeId, Long rubricItemId, String content,
                              BigDecimal suggestedScore, String evidenceJson, String issueCodesJson) {
        return new Feedback(gradeId, rubricItemId, null, FeedbackSource.AI, content,
                suggestedScore, evidenceJson, issueCodesJson);
    }

    public static Feedback teacher(Long gradeId, Long rubricItemId, Long authorId,
                                   String content, BigDecimal finalScore) {
        Feedback feedback = new Feedback(gradeId, rubricItemId, authorId, FeedbackSource.TEACHER,
                content, null, null, null);
        feedback.finalScore = finalScore;
        return feedback;
    }

    public void confirm(Long authorId, BigDecimal finalScore, String content) {
        this.authorId = authorId;
        this.finalScore = finalScore;
        if (content != null && !content.isBlank()) this.content = content;
    }

    public Long getGradeId() { return gradeId; }
    public Long getRubricItemId() { return rubricItemId; }
    public Long getAuthorId() { return authorId; }
    public FeedbackSource getSource() { return source; }
    public String getContent() { return content; }
    public BigDecimal getSuggestedScore() { return suggestedScore; }
    public BigDecimal getFinalScore() { return finalScore; }
    public String getEvidenceJson() { return evidence; }
    public String getIssueCodesJson() { return issueCodes; }
}
