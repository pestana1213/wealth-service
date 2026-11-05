package com.finance.wealth_service.account.port;

import com.finance.wealth_service.auth.AuthenticatedUser;

import java.util.Optional;
import java.util.UUID;

/** Maps the token's identity to the local user id. */
public interface UserPort {

    Optional<UUID> findUserId(AuthenticatedUser user);

    /** Like {@link #findUserId} but answers 404 when the caller was never provisioned through {@code /users/me}. */
    UUID requireUserId(AuthenticatedUser user);
}
