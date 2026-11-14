package com.unilodge.common.dto;

import java.util.UUID;

/**
 * Contact details for a landlord. Email and phone are only populated for
 * signed-in users so they cannot be scraped anonymously.
 */
public record LandlordContactDto(
        UUID id,
        String fullName,
        String businessName,
        String email,
        String phone,
        String avatarUrl
) {
}
