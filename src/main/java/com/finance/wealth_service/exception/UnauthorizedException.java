package com.finance.wealth_service.exception;

/** The identity provider rejected the caller's token. Rendered as 401. */
public class UnauthorizedException extends WealthException {
    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
