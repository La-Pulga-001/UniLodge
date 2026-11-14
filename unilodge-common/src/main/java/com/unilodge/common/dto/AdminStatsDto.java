package com.unilodge.common.dto;

import java.time.Instant;

/** Aggregated counters and recent activity for the admin overview. */
public record AdminStatsDto(
        long totalStudents,
        long totalLandlords,
        long pendingLandlords,
        long suspendedUsers,
        long totalProperties,
        long pendingProperties,
        long approvedProperties,
        long rejectedProperties,
        long totalPosts,
        long hiddenPosts,
        long totalComments,
        long totalAdmins,
        long originalAdmins,
        long newUsersLast7Days,
        long totalSavedProperties,
        Instant generatedAt
) {
}
