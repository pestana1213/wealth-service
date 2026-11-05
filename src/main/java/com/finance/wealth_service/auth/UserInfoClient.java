package com.finance.wealth_service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.finance.wealth_service.exception.IdentityProviderException;
import com.finance.wealth_service.exception.UnauthorizedException;

import java.util.Map;

/**
 * Asks Zitadel's userinfo endpoint who the caller is. Only registration uses it,
 * so the request path of every other endpoint never leaves the service. The
 * profile comes straight from the identity provider, so a caller cannot claim
 * a name or email that is not theirs.
 */
@Component
public class UserInfoClient {
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    private final RestClient http = RestClient.create();
    private final String issuer;
    // Looked up from the discovery document on first use, so the service starts
    // even when Zitadel is briefly unreachable. A failed lookup is retried.
    private volatile String userInfoEndpoint;

    public UserInfoClient(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        this.issuer = issuer.replaceAll("/+$", "");
    }

    public UserProfile fetch(String accessToken) {
        Map<String, Object> claims;
        try {
            claims = http.get().uri(endpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(JSON);
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new UnauthorizedException("Token rejected by the identity provider", exception);
        } catch (RestClientException exception) {
            throw new IdentityProviderException("Identity provider unavailable", exception);
        }
        if (claims == null) {
            throw new IdentityProviderException("Identity provider returned no profile");
        }
        String name = firstText(claims, "name", "preferred_username");
        if (name == null) {
            throw new IdentityProviderException("Identity provider returned no name");
        }
        // An unverified address could squat on someone else's email, which is unique here.
        String email = Boolean.TRUE.equals(claims.get("email_verified")) ? firstText(claims, "email") : null;
        return new UserProfile(name.strip(), email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT));
    }

    private String endpoint() {
        String endpoint = userInfoEndpoint;
        if (endpoint != null) {
            return endpoint;
        }
        try {
            Map<String, Object> discovery = http.get().uri(issuer + "/.well-known/openid-configuration")
                    .retrieve().body(JSON);
            Object found = discovery == null ? null : discovery.get("userinfo_endpoint");
            if (!(found instanceof String text) || text.isBlank()) {
                throw new IdentityProviderException("Identity provider advertises no userinfo endpoint");
            }
            userInfoEndpoint = text;
            return text;
        } catch (RestClientException exception) {
            throw new IdentityProviderException("Identity provider unavailable", exception);
        }
    }

    private static String firstText(Map<String, Object> claims, String... keys) {
        for (String key : keys) {
            if (claims.get(key) instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }
}
