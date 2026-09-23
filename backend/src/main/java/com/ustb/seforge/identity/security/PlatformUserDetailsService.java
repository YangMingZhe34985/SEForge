package com.ustb.seforge.identity.security;

import com.ustb.seforge.identity.domain.User;
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

    public PlatformUserDetailsService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier == null ? "" : identifier.trim();
        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(normalized, normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getId());
        return new UserPrincipal(
                user.getId(), user.getUsername(), user.getPasswordHash(), user.isEnabled(), user.isLocked(), roles);
    }
}
