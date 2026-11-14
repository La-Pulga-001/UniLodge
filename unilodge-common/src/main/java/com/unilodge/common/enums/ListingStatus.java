package com.unilodge.common.enums;

/** Moderation lifecycle of a property listing. */
public enum ListingStatus {
    /** Submitted by a landlord, waiting for administrator approval. */
    PENDING_APPROVAL,
    /** Visible to students. */
    APPROVED,
    /** Rejected by an administrator; only visible to the landlord and admins. */
    REJECTED,
    /** Temporarily unpublished by an administrator. */
    SUSPENDED,
    /** Withdrawn by the landlord. */
    REMOVED
}
