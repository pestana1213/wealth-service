package com.finance.wealth_service.exception;

/** The request is well-formed but asks for something the rules do not allow. Rendered as 400. */
public class InvalidRequestException extends WealthException {
    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
