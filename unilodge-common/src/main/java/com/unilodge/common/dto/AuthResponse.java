package com.unilodge.common.dto;

/** Issued after a successful sign-in or setup. */
public record AuthResponse(String token, UserResponse user) {
}
