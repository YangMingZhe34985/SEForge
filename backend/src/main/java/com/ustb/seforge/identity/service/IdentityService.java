package com.ustb.seforge.identity.service;

import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.api.RegisterRequest;
import com.ustb.seforge.identity.api.UserView;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.domain.GlobalRole;
import com.ustb.seforge.identity.domain.Role;
import com.ustb.seforge.identity.domain.User;
import com.ustb.seforge.identity.domain.UserProfile;
import com.ustb.seforge.identity.domain.UserRole;
import com.ustb.seforge.identity.repository.RoleRepository;
import com.ustb.seforge.identity.repository.UserProfileRepository;
import com.ustb.seforge.identity.repository.UserRepository;
import com.ustb.seforge.identity.repository.UserRoleRepository;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserView registerStudent(RegisterRequest request) {
        return createUser(new CreateUserRequest(
                request.email(), request.username(), request.password(), request.displayName(),
                AccountType.STUDENT, Set.of(GlobalRole.USER)));
    }

    @Transactional
    public UserView createUser(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        String username = request.username().trim().toLowerCase();
        ensureUnique(email, username);

        User user = userRepository.save(new User(email, username, passwordEncoder.encode(request.password())));
        UserProfile profile = profileRepository.save(
                new UserProfile(user.getId(), request.displayName().trim(), request.accountType()));

        Set<GlobalRole> requestedRoles = request.roles() == null || request.roles().isEmpty()
                ? EnumSet.of(GlobalRole.USER)
                : EnumSet.copyOf(request.roles());
        requestedRoles.add(GlobalRole.USER);
        for (GlobalRole requestedRole : requestedRoles) {
            Role role = roleRepository.findByCode(requestedRole.name())
                    .orElseThrow(() -> new IllegalStateException("Missing seeded role " + requestedRole.name()));
            userRoleRepository.save(new UserRole(user.getId(), role.getId()));
        }
        return toView(user, profile, requestedRoles.stream().map(Enum::name).toList());
    }

    @Transactional(readOnly = true)
    public UserView getUser(Long userId) {
        User user = requireUser(userId);
        UserProfile profile = requireProfile(userId);
        return toView(user, profile, userRoleRepository.findRoleCodesByUserId(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> listUsers(int page, int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<Long, UserProfile> profiles = new HashMap<>();
        profileRepository.findAllByUserIdIn(userIds).forEach(profile -> profiles.put(profile.getUserId(), profile));
        List<UserView> views = users.stream()
                .map(user -> toView(user, profiles.get(user.getId()), userRoleRepository.findRoleCodesByUserId(user.getId())))
                .toList();
        return new PageResponse<>(views, users.getNumber(), users.getSize(), users.getTotalElements());
    }

    @Transactional
    public void markLoggedIn(Long userId) {
        requireUser(userId).markLoggedIn();
    }

    @Transactional
    public UserView updateEnabled(Long targetUserId, Long actorUserId, boolean enabled) {
        if (targetUserId.equals(actorUserId) && !enabled) {
            throw new AppException(ErrorCode.CONFLICT, "Administrators cannot disable their own account");
        }
        User user = requireUser(targetUserId);
        user.setEnabled(enabled);
        return toView(user, requireProfile(targetUserId), userRoleRepository.findRoleCodesByUserId(targetUserId));
    }

    @Transactional
    public UserView updateRoles(Long targetUserId, Long actorUserId, Set<GlobalRole> roles) {
        Set<GlobalRole> requested = roles == null || roles.isEmpty()
                ? EnumSet.of(GlobalRole.USER)
                : EnumSet.copyOf(roles);
        requested.add(GlobalRole.USER);
        if (targetUserId.equals(actorUserId) && !requested.contains(GlobalRole.ADMIN)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Administrators cannot remove their own administrator role");
        }

        User user = requireUser(targetUserId);
        UserProfile profile = requireProfile(targetUserId);
        Map<GlobalRole, Role> resolved = new LinkedHashMap<>();
        for (GlobalRole roleCode : requested) {
            resolved.put(roleCode, roleRepository.findByCode(roleCode.name())
                    .orElseThrow(() -> new IllegalStateException("Missing seeded role " + roleCode.name())));
        }
        userRoleRepository.deleteAllByUserId(targetUserId);
        userRoleRepository.flush();
        resolved.values().forEach(role -> userRoleRepository.save(new UserRole(targetUserId, role.getId())));
        return toView(user, profile, requested.stream().map(Enum::name).toList());
    }

    @Transactional(readOnly = true)
    public boolean isTeacher(Long userId) {
        return profileRepository.findByUserId(userId)
                .map(UserProfile::getAccountType)
                .filter(AccountType.TEACHER::equals)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        profileRepository.findAllByUserIdIn(userIds)
                .forEach(profile -> result.put(profile.getUserId(), profile.getDisplayName()));
        return result;
    }

    private void ensureUnique(String email, String username) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS, "Username is already registered");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    private UserProfile requireProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User profile not found"));
    }

    private UserView toView(User user, UserProfile profile, Collection<String> roleCodes) {
        if (profile == null) {
            throw new IllegalStateException("User profile is missing for user " + user.getId());
        }
        return new UserView(
                user.getId(), user.getEmail(), user.getUsername(), profile.getDisplayName(), profile.getAccountType(),
                new LinkedHashSet<>(roleCodes), user.isEnabled());
    }
}
