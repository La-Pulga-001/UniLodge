package com.unilodge.common.dto;

/** Result of an address lookup against the geocoding provider. */
public record GeocodeResult(String displayName, double latitude, double longitude) {
}
