package com.unilodge.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentPostRequest(
        @NotBlank @Size(min = 5, max = 120) String title,
        @NotBlank @Size(min = 10, max = 4000) String body,
        @NotNull UUID universityId,
        @Size(max = 160) String preferredLocation,
        @DecimalMin(value = "0.0", message = "Budget cannot be negative") BigDecimal budgetMax,
        @Min(1) @Max(10) Integer roommatesNeeded
) {
}
