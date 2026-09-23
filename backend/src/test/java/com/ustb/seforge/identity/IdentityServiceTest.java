package com.ustb.seforge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.identity.api.RegisterRequest;
import com.ustb.seforge.identity.api.UserView;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.domain.GlobalRole;
import com.ustb.seforge.identity.domain.Role;
import com.ustb.seforge.identity.domain.User;
import com.ustb.seforge.identity.domain.UserProfile;
import com.ustb.seforge.identity.repository.RoleRepository;
import com.ustb.seforge.identity.repository.UserProfileRepository;
import com.ustb.seforge.identity.repository.UserRepository;
import com.ustb.seforge.identity.repository.UserRoleRepository;
import com.ustb.seforge.identity.service.IdentityService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserProfileRepository profileRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock PasswordEncoder passwordEncoder;

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(
                userRepository, profileRepository, roleRepository, userRoleRepository, passwordEncoder);
    }

    @Test
    void studentRegistrationNormalizesIdentifiersAndHashesPassword() {
        Role userRole = org.mockito.Mockito.mock(Role.class);
        when(passwordEncoder.encode("a-secure-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByCode("USER")).thenReturn(Optional.of(userRole));

        UserView result = service.registerStudent(new RegisterRequest(
                "  Learner@Example.COM ", " Learner.One ", "a-secure-password", "Learner One"));

        assertThat(result.email()).isEqualTo("learner@example.com");
        assertThat(result.username()).isEqualTo("learner.one");
        assertThat(result.accountType()).isEqualTo(AccountType.STUDENT);
        assertThat(result.roles()).containsExactly("USER");
        verify(passwordEncoder).encode("a-secure-password");
    }

    @Test
    void registrationRejectsDuplicateEmailBeforeHashingPassword() {
        when(userRepository.existsByEmailIgnoreCase("learner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.registerStudent(new RegisterRequest(
                "learner@example.com", "learner", "a-secure-password", "Learner")))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void administratorCannotRemoveOwnAdministratorRole() {
        assertThatThrownBy(() -> service.updateRoles(7L, 7L, Set.of(GlobalRole.USER)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void roleUpdateAlwaysKeepsTheBaselineUserRole() {
        User user = new User("teacher@example.com", "teacher", "hash");
        UserProfile profile = new UserProfile(8L, "Teacher", AccountType.TEACHER);
        Role userRole = org.mockito.Mockito.mock(Role.class);
        Role adminRole = org.mockito.Mockito.mock(Role.class);
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(8L)).thenReturn(Optional.of(profile));
        when(roleRepository.findByCode("USER")).thenReturn(Optional.of(userRole));
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));

        UserView result = service.updateRoles(8L, 7L, Set.of(GlobalRole.ADMIN));

        assertThat(result.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        verify(userRoleRepository).deleteAllByUserId(8L);
        verify(userRoleRepository).flush();
    }
}
