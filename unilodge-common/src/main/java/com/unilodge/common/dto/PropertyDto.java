package com.unilodge.common.dto;

import com.unilodge.common.enums.FurnishedStatus;
import com.unilodge.common.enums.ListingStatus;
import com.unilodge.common.enums.PropertyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Full property representation used on detail pages and dashboards. */
public record PropertyDto(
        UUID id,
        UUID universityId,
        String universityName,
        String title,
        String description,
        PropertyType propertyType,
        BigDecimal rentAmount,
        boolean negotiable,
        boolean utilitiesIncluded,
        String utilitiesDescription,
        FurnishedStatus furnished,
        Integer bedrooms,
        Integer bathrooms,
        String address,
        String city,
        String region,
        Double latitude,
        Double longitude,
        Double distanceToCampusKm,
        LocalDate moveInDate,
        String leaseTerms,
        List<String> amenities,
        ListingStatus status,
        String adminNote,
        List<PropertyImageDto> images,
        String coverImageUrl,
        LandlordContactDto landlord,
        boolean saved,
        long viewCount,
        Instant createdAt,
        Instant updatedAt
) {
}
