package com.ustb.seforge.assignment.api;

import com.fasterxml.jackson.databind.JsonNode;

public record SubmissionAnswerView(Long questionId, JsonNode answer,
                                   String attachmentObjectKey, String attachmentFileName) {
}
