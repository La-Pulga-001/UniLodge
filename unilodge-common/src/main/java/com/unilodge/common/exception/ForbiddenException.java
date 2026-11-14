package com.unilodge.common.exception;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(403, "FORBIDDEN", message);
    }

    public ForbiddenException(String code, String message) {
        super(403, code, message);
    }
}
