package com.unilodge.service.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.unilodge.common.dto.GeocodeResult;
import com.unilodge.common.exception.BadRequestException;
import com.unilodge.service.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proxies address lookups to OpenStreetMap's Nominatim service so landlords can
 * drop an accurate pin. Results are cached briefly to respect the provider's
 * usage policy, and a descriptive User-Agent is always sent.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private record CachedResults(List<GeocodeResult> results, Instant fetchedAt) {
    }

    private final AppProperties properties;
    private final RestClient restClient;
    private final Map<String, CachedResults> cache = new ConcurrentHashMap<>();

    public GeocodingService(AppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getGeocoding().getBaseUrl())
                .defaultHeader("User-Agent", properties.getGeocoding().getUserAgent())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public List<GeocodeResult> search(String query) {
        if (query == null || query.trim().length() < 3) {
            throw new BadRequestException("Enter at least three characters to search for an address.");
        }
        if (!properties.getGeocoding().isEnabled()) {
            throw new BadRequestException("Address lookup is currently disabled.");
        }
        String key = query.trim().toLowerCase();
        CachedResults cached = cache.get(key);
        if (cached != null && cached.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.results();
        }

        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 5)
                            .queryParam("countrycodes", "gh")
                            .queryParam("q", query.trim())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            List<GeocodeResult> results = new ArrayList<>();
            if (body != null && body.isArray()) {
                for (JsonNode node : body) {
                    results.add(new GeocodeResult(
                            node.path("display_name").asText(),
                            node.path("lat").asDouble(),
                            node.path("lon").asDouble()));
                }
            }
            cache.put(key, new CachedResults(results, Instant.now()));
            if (cache.size() > 500) {
                cache.clear();
            }
            return results;
        } catch (Exception exception) {
            log.warn("Geocoding lookup failed for '{}': {}", query, exception.getMessage());
            throw new BadRequestException("Address lookup is unavailable right now. "
                    + "You can still place the pin directly on the map.");
        }
    }
}
