package com.finance.wealth_service.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class UserAuthentication extends AbstractAuthenticationToken {
    private final AuthenticatedUser user;
    private final Jwt jwt;

    public UserAuthentication(AuthenticatedUser user, Jwt jwt) {
        super(List.of());
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override public AuthenticatedUser getPrincipal() { return user; }

    @Override public Jwt getCredentials() { return jwt; }
}

