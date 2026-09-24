package com.ustb.seforge.identity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.domain.GlobalRole;
import com.ustb.seforge.identity.repository.UserRoleRepository;
import com.ustb.seforge.identity.service.AdminBootstrap;
import com.ustb.seforge.identity.service.IdentityService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminBootstrapTest {
    private final IdentityService identityService = mock(IdentityService.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(identityService, userRoleRepository,
                email, "admin", password, "SEForge Administrator");
    }

    @Test
    void createsAdministratorWhenNoneExists() {
        when(userRoleRepository.existsAnyByRoleCode(GlobalRole.ADMIN.name())).thenReturn(false);
        bootstrap("admin@admin.com", "admin12345678").run(null);
        ArgumentCaptor<CreateUserRequest> captor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(identityService).createUser(captor.capture());
        CreateUserRequest request = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("admin@admin.com", request.email());
        org.junit.jupiter.api.Assertions.assertEquals(AccountType.PLATFORM, request.accountType());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(GlobalRole.ADMIN, GlobalRole.USER), request.roles());
    }

    @Test
    void skipsBootstrapWhenAdministratorExists() {
        when(userRoleRepository.existsAnyByRoleCode(GlobalRole.ADMIN.name())).thenReturn(true);
        bootstrap("admin@admin.com", "admin12345678").run(null);
        verify(identityService, never()).createUser(any());
    }

    @Test
    void skipsBootstrapWhenCredentialsAreBlank() {
        when(userRoleRepository.existsAnyByRoleCode(GlobalRole.ADMIN.name())).thenReturn(false);
        assertDoesNotThrow(() -> bootstrap("", "").run(null));
        verify(identityService, never()).createUser(any());
    }

    @Test
    void degradesToWarningWhenPasswordIsTooShort() {
        when(userRoleRepository.existsAnyByRoleCode(GlobalRole.ADMIN.name())).thenReturn(false);
        assertDoesNotThrow(() -> bootstrap("admin@admin.com", "short").run(null));
        verify(identityService, never()).createUser(any());
    }

    @Test
    void degradesToWarningWhenUsernameAlreadyExists() {
        when(userRoleRepository.existsAnyByRoleCode(GlobalRole.ADMIN.name())).thenReturn(false);
        when(identityService.createUser(any()))
                .thenThrow(new AppException(ErrorCode.USERNAME_ALREADY_EXISTS, "Username already exists"));
        assertDoesNotThrow(() -> bootstrap("admin@admin.com", "admin12345678").run(null));
    }
}
