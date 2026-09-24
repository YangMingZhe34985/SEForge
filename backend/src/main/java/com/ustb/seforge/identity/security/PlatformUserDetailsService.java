package com.ustb.seforge.identity.security;

import com.ustb.seforge.identity.domain.User;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.api.LoginPortal;
import com.ustb.seforge.identity.repository.UserProfileRepository;
import com.ustb.seforge.identity.repository.UserRepository;
import com.ustb.seforge.identity.repository.UserRoleRepository;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository profileRepository;

    public PlatformUserDetailsService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                                      UserProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        int separator = identifier == null ? -1 : identifier.indexOf(':');
        if (separator < 1) throw new UsernameNotFoundException("Invalid credentials");
        LoginPortal portal;
        try {
            portal = LoginPortal.valueOf(identifier.substring(0, separator));
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        String normalized = identifier.substring(separator + 1).trim();
        User user = portal == LoginPortal.STUDENT
                ? profileRepository.findByStudentNoIgnoreCase(normalized)
                        .flatMap(profile -> userRepository.findById(profile.getUserId()))
                        .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"))
                : userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(normalized, normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getId());
        if (portal == LoginPortal.ADMIN && !roles.contains("ADMIN")) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        if (portal == LoginPortal.TEACHER && profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getAccountType() == AccountType.TEACHER).orElse(false) == false) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        if (portal == LoginPortal.STUDENT && profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getAccountType() == AccountType.STUDENT).orElse(false) == false) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return new UserPrincipal(
                user.getId(), user.getUsername(), user.getPasswordHash(), user.isEnabled(), user.isLocked(), roles);
    }
}
