package com.ustb.seforge.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordResetRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 10, max = 72) String password) {
}
