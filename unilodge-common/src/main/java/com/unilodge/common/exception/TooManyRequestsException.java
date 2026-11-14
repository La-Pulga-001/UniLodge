package com.unilodge.common.exception;

public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(429, "TOO_MANY_REQUESTS", message);
    }
}
