package com.finance.wealth_service.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

public class UserAuthentication extends AbstractAuthenticationToken {
    private final AuthenticatedUser user;
    private final Jwt jwt;

    public UserAuthentication(AuthenticatedUser user, Jwt jwt) {
        super(List.of());
        this.user = Objects.requireNonNull(user, "user");
        this.jwt = Objects.requireNonNull(jwt, "jwt");
        setAuthenticated(true);
    }

    @Override public @NonNull AuthenticatedUser getPrincipal() { return user; }

    @Override public @NonNull Jwt getCredentials() { return jwt; }
}

