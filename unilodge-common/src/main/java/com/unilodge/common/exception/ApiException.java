package com.unilodge.common.exception;

/**
 * Base runtime exception for expected API failures. Carries an HTTP status and a
 * stable machine readable error code that the frontend can react to.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
