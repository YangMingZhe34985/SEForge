package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.TutorOperation;
import java.util.List;

public record TutorResponseView(
        Long interactionId,
        TutorOperation action,
        String content,
        boolean allowed,
        String policyMessage,
        List<TutorCitationView> citations) {
}
