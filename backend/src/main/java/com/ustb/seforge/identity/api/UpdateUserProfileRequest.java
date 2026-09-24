package com.ustb.seforge.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,64}$") String studentNo) {
}
