package com.dayan.platform.security;

import com.dayan.platform.model.UserAccount;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record PlatformUserPrincipal(
        long id,
        String username,
        String password,
        boolean enabled,
        List<GrantedAuthority> authorities
) implements UserDetails {

    public static PlatformUserPrincipal from(UserAccount user, List<String> permissions) {
        List<GrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        return new PlatformUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getEnabled()),
                authorities
        );
    }

    public PlatformUserPrincipal {
        authorities = List.copyOf(authorities);
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
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
