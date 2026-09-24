package com.ustb.seforge.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.api.UserImportPreviewView;
import com.ustb.seforge.identity.api.UserImportResultView;
import com.ustb.seforge.identity.api.UserView;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.repository.UserProfileRepository;
import com.ustb.seforge.identity.repository.UserRepository;
import com.ustb.seforge.identity.service.IdentityService;
import com.ustb.seforge.identity.service.UserImportService;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserImportServiceTest {
    private final IdentityService identity = mock(IdentityService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final UserImportService service = new UserImportService(identity, users, profiles);

    @Test
    void previewIsReadOnlyAndReportsDuplicateStudentNumber() {
        byte[] csv = csv("STUDENT,2026-001,alice,alice@example.test,Alice\n"
                + "STUDENT,2026-001,bob,bob@example.test,Bob\n");
        UserImportPreviewView preview = service.preview(csv);
        assertEquals(2, preview.total());
        assertEquals(1, preview.valid());
        assertEquals("REJECTED", preview.rows().get(1).status());
        assertTrue(preview.rows().get(1).message().contains("Duplicate student number"));
        verify(identity, never()).createUser(any());
    }

    @Test
    void confirmationRequiresUnchangedBytesAndReportsPartialSuccess() {
        byte[] csv = csv("STUDENT,2026-001,alice,alice@example.test,Alice\n"
                + "STUDENT,2026-001,bob,bob@example.test,Bob\n");
        String digest = service.preview(csv).digest();
        assertThrows(AppException.class, () -> service.confirm(csv("TEACHER,,carl,carl@example.test,Carl\n"), digest));
        when(identity.createUser(any(CreateUserRequest.class))).thenAnswer(invocation -> {
            CreateUserRequest request = invocation.getArgument(0);
            return new UserView(42L, request.email(), request.username(), request.displayName(),
                    AccountType.STUDENT, request.studentNo(), Set.of("USER"), true);
        });
        UserImportResultView result = service.confirm(csv, digest);
        assertEquals(1, result.created());
        assertEquals(1, result.skipped());
        assertEquals(0, result.failed());
        assertEquals("CREATED", result.rows().getFirst().status());
        assertTrue(result.rows().getFirst().initialPassword().length() >= 10);
    }

    @Test
    void repeatedImportSkipsExistingAccounts() {
        byte[] csv = csv("STUDENT,2026-001,alice,alice@example.test,Alice\n");
        String digest = service.preview(csv).digest();
        when(users.existsByUsernameIgnoreCase("alice")).thenReturn(true);
        UserImportResultView result = service.confirm(csv, digest);
        assertEquals(0, result.created());
        assertEquals(1, result.skipped());
        verify(identity, never()).createUser(any());
    }

    @Test
    void rejectsMalformedOrUnboundedCsv() {
        assertThrows(AppException.class, () -> service.preview(csv("STUDENT,2026-001,\"alice,alice@example.test,Alice\n")));
        assertThrows(AppException.class, () -> service.preview(new byte[1024 * 1024 + 1]));
    }

    private byte[] csv(String rows) {
        return ("accountType,studentNo,username,email,displayName\n" + rows).getBytes(StandardCharsets.UTF_8);
    }
}
