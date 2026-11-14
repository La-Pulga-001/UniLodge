package com.unilodge.common.enums;

/** Lifecycle of a user account. */
public enum UserStatus {
    /** Landlord application awaiting administrator approval. */
    PENDING_APPROVAL,
    /** Account may sign in normally. */
    ACTIVE,
    /** Landlord application rejected by an administrator. */
    REJECTED,
    /** Account suspended by an administrator. */
    SUSPENDED
}
