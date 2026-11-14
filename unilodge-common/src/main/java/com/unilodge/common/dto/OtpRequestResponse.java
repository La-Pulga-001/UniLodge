package com.unilodge.common.dto;

/**
 * Result of requesting an OTP. {@code devCode} is only populated in development
 * when {@code app.mail.expose-otp} is enabled.
 */
public record OtpRequestResponse(String message, String devCode) {
}
