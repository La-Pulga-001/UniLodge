package com.unilodge.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request a setup code for an administrator account with no password yet. */
public record SetupOtpRequest(@NotBlank @Email String email) {
}
