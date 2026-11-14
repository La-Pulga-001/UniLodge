package com.unilodge.web.web;

import com.unilodge.common.dto.GeocodeResult;
import com.unilodge.service.geo.GeocodingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Address lookup for the listing map picker. */
@RestController
@RequestMapping("/api/geo")
public class GeocodingController {

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @GetMapping("/search")
    public List<GeocodeResult> search(@RequestParam("q") String query) {
        return geocodingService.search(query);
    }
}
