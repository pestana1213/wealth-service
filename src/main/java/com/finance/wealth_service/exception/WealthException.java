package com.finance.wealth_service.exception;

/**
 * Base of the service's own failures. Each subtype names what went wrong, never
 * how it is rendered: {@link GlobalExceptionHandler} picks the status code, and
 * the message becomes the problem detail, so it must be safe to show a client.
 */
public abstract class WealthException extends RuntimeException {
    protected WealthException(String message) {
        super(message);
    }

    protected WealthException(String message, Throwable cause) {
        super(message, cause);
    }
}
