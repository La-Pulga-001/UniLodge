package com.unilodge.common.exception;

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(400, "BAD_REQUEST", message);
    }

    public BadRequestException(String code, String message) {
        super(400, code, message);
    }
}
