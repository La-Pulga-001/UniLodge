package com.unilodge.common.util;

/** Geographic helpers used to work out how far a listing is from campus. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {
    }

    /**
     * Great-circle distance between two coordinates, in kilometres.
     */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /** Rounds a distance to one decimal place for display and filtering. */
    public static double roundKm(double km) {
        return Math.round(km * 10.0) / 10.0;
    }
}
