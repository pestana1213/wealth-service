package com.finance.wealth_service.exception;

/** The resource does not exist, or belongs to someone else: the caller cannot tell the two apart. Rendered as 404. */
public class NotFoundException extends WealthException {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
