package com.ustb.seforge.conversation.api;

import com.ustb.seforge.conversation.domain.FeedbackRating;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(@NotNull FeedbackRating rating, @Size(max = 2000) String comment) {
}
