package com.ustb.seforge.assignment.domain;

public enum QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER,
    ANALYSIS,
    DESIGN,
    CODE,
    DOCUMENT_REPORT;

    public boolean objective() {
        return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE || this == TRUE_FALSE;
    }
}
