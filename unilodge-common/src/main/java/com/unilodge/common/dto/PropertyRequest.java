package com.unilodge.common.dto;

import com.unilodge.common.enums.FurnishedStatus;
import com.unilodge.common.enums.PropertyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Payload used by landlords to create or update a listing. */
public record PropertyRequest(
        @NotBlank @Size(min = 5, max = 120) String title,
        @NotBlank @Size(min = 20, max = 4000) String description,
        @NotNull UUID universityId,
        @NotNull PropertyType propertyType,
        @NotNull @DecimalMin(value = "0.0", message = "Rent cannot be negative")
        @Digits(integer = 10, fraction = 2) BigDecimal rentAmount,
        boolean negotiable,
        boolean utilitiesIncluded,
        @Size(max = 300) String utilitiesDescription,
        @NotNull FurnishedStatus furnished,
        @NotNull @Min(0) @Max(20) Integer bedrooms,
        @NotNull @Min(0) @Max(20) Integer bathrooms,
        @NotBlank @Size(max = 200) String address,
        @NotBlank @Size(max = 120) String city,
        @Size(max = 120) String region,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull LocalDate moveInDate,
        @Size(max = 500) String leaseTerms,
        List<@Size(max = 60) String> amenities
) {
}
