package com.ustb.seforge.identity.api;

import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.domain.GlobalRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{3,64}$", message = "用户名须为 3–64 位英文字母、数字、下划线、点或短横线") String username,
        @NotBlank @Size(min = 10, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName,
        @NotNull AccountType accountType,
        Set<GlobalRole> roles,
        String studentNo) {
    public CreateUserRequest(String email, String username, String password, String displayName,
                             AccountType accountType, Set<GlobalRole> roles) {
        this(email, username, password, displayName, accountType, roles, null);
    }
}
