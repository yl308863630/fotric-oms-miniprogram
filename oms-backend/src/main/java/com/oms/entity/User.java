package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@Entity
@Table(name = "users")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String role; // ROLE_USER, ROLE_ADMIN

    private String realName;

    private String phone; // 手机号码

    private String department; // 所属部门

    private String companyTitle; // 所在公司抬头

    private String permissions; // 权限列表，逗号分隔，例如：product,opportunity,sales,purchase,user

    private java.time.LocalDateTime createTime;

    private java.time.LocalDateTime lastLoginTime;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty("enabled")
    private Boolean enabled = true; // 是否启用

    @PrePersist
    public void onCreate() {
        if (createTime == null) {
            createTime = java.time.LocalDateTime.now();
        }
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isAccountNonExpired() { return true; }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isAccountNonLocked() { return true; }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEnabled() {
        return enabled != null ? enabled : true;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }
}
