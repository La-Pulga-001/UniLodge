package com.unilodge.common.enums;

/** Purpose of a one-time password code. */
public enum OtpPurpose {
    /** Password recovery for an existing account. */
    PASSWORD_RESET,
    /** First-time password setup for an administrator account. */
    ADMIN_SETUP,
    /** Email verification for a promoted administrator. */
    ADMIN_VERIFICATION
}
