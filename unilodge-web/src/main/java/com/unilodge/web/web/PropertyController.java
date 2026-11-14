package com.unilodge.web.web;

import com.unilodge.common.dto.*;
import com.unilodge.common.enums.FurnishedStatus;
import com.unilodge.common.enums.ListingStatus;
import com.unilodge.common.enums.PropertyType;
import com.unilodge.common.enums.UserRole;
import com.unilodge.persistence.entity.User;
import com.unilodge.service.property.PropertyService;
import com.unilodge.web.security.AuthSupport;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Student-facing property search and landlord listing management. */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public PagedResponse<PropertySummaryDto> search(
            Authentication authentication,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID universityId,
            @RequestParam(required = false) BigDecimal minRent,
            @RequestParam(required = false) BigDecimal maxRent,
            @RequestParam(required = false) FurnishedStatus furnished,
            @RequestParam(required = false) PropertyType propertyType,
            @RequestParam(required = false) Integer minBedrooms,
            @RequestParam(required = false) Double maxDistanceKm,
            @RequestParam(required = false) UUID landlordId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        User viewer = AuthSupport.current(authentication);
        boolean admin = viewer != null && viewer.getRole() == UserRole.ADMIN && viewer.isAdminVerified();
        ListingStatus effectiveStatus = admin && status != null ? status : ListingStatus.APPROVED;
        return propertyService.search(effectiveStatus, q, universityId, minRent, maxRent, furnished,
                propertyType, minBedrooms, maxDistanceKm, landlordId, sort, page, size);
    }

    @GetMapping("/featured")
    public List<PropertySummaryDto> featured() {
        return propertyService.featured();
    }

    @GetMapping("/mine")
    public PagedResponse<PropertySummaryDto> mine(Authentication authentication,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return propertyService.mine(AuthSupport.require(authentication), page, size);
    }

    @GetMapping("/saved")
    public List<PropertySummaryDto> saved(Authentication authentication) {
        return propertyService.savedProperties(AuthSupport.require(authentication));
    }

    @GetMapping("/saved/ids")
    public List<UUID> savedIds(Authentication authentication) {
        return propertyService.savedIds(AuthSupport.require(authentication));
    }

    @GetMapping("/{id}")
    public PropertyDto detail(Authentication authentication, @PathVariable UUID id) {
        return propertyService.getDetail(AuthSupport.current(authentication), id);
    }

    @PostMapping
    public PropertyDto create(Authentication authentication, @Valid @RequestBody PropertyRequest request) {
        return propertyService.create(AuthSupport.require(authentication), request);
    }

    @PutMapping("/{id}")
    public PropertyDto update(Authentication authentication,
                              @PathVariable UUID id,
                              @Valid @RequestBody PropertyRequest request) {
        return propertyService.update(AuthSupport.require(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse remove(Authentication authentication, @PathVariable UUID id) {
        propertyService.remove(AuthSupport.require(authentication), id);
        return new MessageResponse("Listing removed. You can restore it from your dashboard.");
    }

    @PostMapping("/{id}/restore")
    public PropertyDto restore(Authentication authentication, @PathVariable UUID id) {
        return propertyService.restore(AuthSupport.require(authentication), id);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<PropertyImageDto> addImage(Authentication authentication,
                                           @PathVariable UUID id,
                                           @RequestParam("file") MultipartFile file) {
        return propertyService.addImage(AuthSupport.require(authentication), id, file);
    }

    @PutMapping("/{id}/images/{imageId}/cover")
    public List<PropertyImageDto> setCover(Authentication authentication,
                                           @PathVariable UUID id,
                                           @PathVariable UUID imageId) {
        return propertyService.setCover(AuthSupport.require(authentication), id, imageId);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public List<PropertyImageDto> deleteImage(Authentication authentication,
                                              @PathVariable UUID id,
                                              @PathVariable UUID imageId) {
        return propertyService.deleteImage(AuthSupport.require(authentication), id, imageId);
    }

    @PostMapping("/{id}/save")
    public Map<String, Boolean> toggleSaved(Authentication authentication, @PathVariable UUID id) {
        boolean saved = propertyService.toggleSaved(AuthSupport.require(authentication), id);
        return Map.of("saved", saved);
    }
}
