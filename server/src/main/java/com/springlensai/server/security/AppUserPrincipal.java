package com.springlensai.server.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class AppUserPrincipal implements OAuth2User, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final HashMap<String, Object> attributes;

    public AppUserPrincipal(UUID id, Map<String, Object> attributes) {
        this.id = id;
        this.attributes = new HashMap<>(attributes);
    }

    public UUID getId() {
        return id;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AuthorityUtils.createAuthorityList("ROLE_USER");
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
