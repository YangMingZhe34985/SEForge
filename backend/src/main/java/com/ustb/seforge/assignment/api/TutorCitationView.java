package com.ustb.seforge.assignment.api;

public record TutorCitationView(
        String id,
        Long documentId,
        String documentName,
        String chapter,
        Integer page,
        String section,
        String excerpt) {
}
