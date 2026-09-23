package com.ustb.seforge.identity.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UserPrincipal implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final boolean locked;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(
            Long userId,
            String username,
            String password,
            boolean enabled,
            boolean locked,
            Collection<String> roleCodes) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.locked = locked;
        this.authorities = roleCodes.stream()
                .map(code -> code.startsWith("ROLE_") ? code : "ROLE_" + code)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public Long userId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
