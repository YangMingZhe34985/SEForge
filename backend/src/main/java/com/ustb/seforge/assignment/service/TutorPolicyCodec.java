package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.time.Instant;
import java.util.HashMap;
import org.springframework.stereotype.Component;

@Component
public class TutorPolicyCodec {
    private final ObjectMapper objectMapper;

    public TutorPolicyCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TutorPolicy read(String value) {
        if (value == null || value.isBlank()) return TutorPolicy.defaults();
        try {
            return objectMapper.readValue(value, TutorPolicy.class);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Invalid tutor policy");
        }
    }

    public String write(TutorPolicy value) {
        try {
            return objectMapper.writeValueAsString(value == null ? TutorPolicy.defaults() : value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Invalid tutor policy");
        }
    }

    public String withExtension(String current, Long studentId, Instant dueAt) {
        TutorPolicy policy = read(current);
        var extensions = new HashMap<String, Instant>();
        if (policy.dueAtOverrides() != null) extensions.putAll(policy.dueAtOverrides());
        if (dueAt == null) extensions.remove(studentId.toString());
        else extensions.put(studentId.toString(), dueAt);
        return write(new TutorPolicy(policy.allowFullSolutionBeforeSubmit(), policy.fullSolutionAfterSubmit(),
                policy.fullSolutionAfterDue(), policy.allowLateSubmission(), policy.enabledOperations(), extensions));
    }
}
