package com.unilodge.common.dto;

import com.unilodge.common.enums.FurnishedStatus;
import com.unilodge.common.enums.ListingStatus;
import com.unilodge.common.enums.PropertyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Compact property representation for cards and lists. */
public record PropertySummaryDto(
        UUID id,
        String title,
        PropertyType propertyType,
        BigDecimal rentAmount,
        FurnishedStatus furnished,
        Integer bedrooms,
        Integer bathrooms,
        Double distanceToCampusKm,
        String city,
        String universityName,
        String coverImageUrl,
        LocalDate moveInDate,
        ListingStatus status,
        Instant createdAt
) {
}
