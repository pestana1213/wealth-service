package com.finance.wealth_service.support;

import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.auth.UserAuthentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Users with a token identity, and the request processor that signs in as one of them. */
public final class TestUsers {
    public static final String ISSUER = "https://issuer.test";

    private TestUsers() {
    }

    /** A user with a fresh subject and email, ready to save. */
    public static User newUser(String name) {
        return new User(ISSUER, UUID.randomUUID().toString(), name, UUID.randomUUID() + "@example.com");
    }

    public static AuthenticatedUser identityOf(User user) {
        return new AuthenticatedUser(user.getIssuer(), user.getSubject());
    }

    public static RequestPostProcessor signedInAs(User user) {
        return signedInAs(identityOf(user));
    }

    /** Signs in with a subject of your choosing, provisioned or not. */
    public static RequestPostProcessor signedInAs(AuthenticatedUser identity) {
        Jwt jwt = Jwt.withTokenValue("token-" + identity.subject()).header("alg", "none")
                .issuer(identity.issuer()).subject(identity.subject()).build();
        return authentication(new UserAuthentication(identity, jwt));
    }

    /** A valid identity that was never provisioned through {@code /users/me}. */
    public static AuthenticatedUser stranger() {
        return new AuthenticatedUser(ISSUER, UUID.randomUUID().toString());
    }
}
