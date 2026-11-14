package com.unilodge.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code") String code
) {
}
