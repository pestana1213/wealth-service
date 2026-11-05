package com.finance.wealth_service.exception;

/** The request is valid but clashes with the current state, such as an archived bucket or a concurrent update. Rendered as 409. */
public class ConflictException extends WealthException {
    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
