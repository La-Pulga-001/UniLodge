package com.unilodge.common.exception;

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(409, code, message);
    }
}
