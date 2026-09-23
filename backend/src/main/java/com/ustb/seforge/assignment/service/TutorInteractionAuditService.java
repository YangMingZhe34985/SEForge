package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.domain.TutorInteraction;
import com.ustb.seforge.assignment.domain.TutorOperation;
import com.ustb.seforge.assignment.repository.TutorInteractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TutorInteractionAuditService {
    private final TutorInteractionRepository interactions;

    public TutorInteractionAuditService(TutorInteractionRepository interactions) {
        this.interactions = interactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TutorInteraction start(Long courseId, Long assignmentId, Long questionId, Long submissionId,
                                  Long userId, TutorOperation operation, String input) {
        return interactions.save(new TutorInteraction(courseId, assignmentId, questionId, submissionId,
                userId, operation, input));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long interactionId, String response, Long aiTraceId) {
        interactions.findById(interactionId).ifPresent(value -> value.complete(response, aiTraceId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deny(Long interactionId, String policyMessage) {
        interactions.findById(interactionId).ifPresent(value -> value.deny(policyMessage));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long interactionId, Throwable failure) {
        String message = failure == null ? "Tutor failed" : failure.getClass().getSimpleName();
        interactions.findById(interactionId).ifPresent(value -> value.fail(message));
    }
}
