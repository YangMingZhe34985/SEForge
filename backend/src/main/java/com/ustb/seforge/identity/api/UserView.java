package com.ustb.seforge.identity.api;

import com.ustb.seforge.identity.domain.AccountType;
import java.util.Set;

public record UserView(
        Long id,
        String email,
        String username,
        String displayName,
        AccountType accountType,
        String studentNo,
        Set<String> roles,
        boolean enabled) {
}
