package com.finance.wealth_service.exception;

/** The identity provider could not be reached or answered with something unusable. Rendered as 502. */
public class IdentityProviderException extends WealthException {
    public IdentityProviderException(String message) {
        super(message);
    }

    public IdentityProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
