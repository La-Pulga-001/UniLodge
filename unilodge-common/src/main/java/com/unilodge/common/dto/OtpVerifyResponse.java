package com.unilodge.common.dto;

/**
 * Returned when an OTP is accepted. {@code resetToken} is a short-lived,
 * single-purpose token used to finish the flow. {@code devCode} is only
 * populated when the application runs with console email delivery.
 */
public record OtpVerifyResponse(String resetToken, String devCode) {
}
