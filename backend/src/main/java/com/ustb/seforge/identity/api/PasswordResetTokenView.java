package com.ustb.seforge.identity.api;

import java.time.Instant;

public record PasswordResetTokenView(String token, Instant expiresAt) {
}
