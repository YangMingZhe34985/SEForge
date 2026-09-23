package com.ustb.seforge.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{3,64}$") String username,
        @NotBlank @Size(min = 10, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName) {
}
