package com.ustb.seforge.identity.api;

public record UserImportRowView(int line, String accountType, String studentNo, String username,
                                String email, String displayName, String status, String message,
                                Long userId, String initialPassword) {
}
