package com.oyproj.common.security.domain;

import com.oyproj.common.domain.dto.UserDTO;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class SecurityUser implements UserDetails {

    private UserDTO user;
    private List<GrantedAuthority> authorities;

    public SecurityUser(String userId, int status, List<GrantedAuthority> authorities){
        this.user = new UserDTO();
        this.user.setId(userId);
        this.user.setStatus(status);
        this.authorities = authorities;
    }
    public SecurityUser(UserDTO user, List<GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return user.getId(); // 使用用户ID作为用户名
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() == 1; // 假设1表示启用
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == 1; // 假设1表示启用
    }
    public UserDTO getUser() {
        return user;
    }
}