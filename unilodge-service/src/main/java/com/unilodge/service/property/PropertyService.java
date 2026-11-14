package com.unilodge.service.property;

import com.unilodge.common.dto.PagedResponse;
import com.unilodge.common.dto.PropertyDto;
import com.unilodge.common.dto.PropertyImageDto;
import com.unilodge.common.dto.PropertyRequest;
import com.unilodge.common.dto.PropertySummaryDto;
import com.unilodge.common.enums.FurnishedStatus;
import com.unilodge.common.enums.ListingStatus;
import com.unilodge.common.enums.PropertyType;
import com.unilodge.common.enums.UserRole;
import com.unilodge.common.enums.UserStatus;
import com.unilodge.common.exception.BadRequestException;
import com.unilodge.common.exception.ForbiddenException;
import com.unilodge.common.exception.NotFoundException;
import com.unilodge.persistence.entity.Property;
import com.unilodge.persistence.entity.PropertyImage;
import com.unilodge.persistence.entity.SavedProperty;
import com.unilodge.persistence.entity.University;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.PropertyImageRepository;
import com.unilodge.persistence.repository.PropertyRepository;
import com.unilodge.persistence.repository.SavedPropertyRepository;
import com.unilodge.persistence.specification.PropertySpecifications;
import com.unilodge.common.util.GeoUtils;
import com.unilodge.service.config.AppProperties;
import com.unilodge.service.mapper.DtoMapper;
import com.unilodge.service.storage.FileStorageService;
import com.unilodge.service.university.UniversityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Search, publish, edit and bookmark property listings. */
@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository imageRepository;
    private final SavedPropertyRepository savedRepository;
    private final UniversityService universityService;
    private final FileStorageService fileStorageService;
    private final AppProperties properties;

    public PropertyService(PropertyRepository propertyRepository,
                           PropertyImageRepository imageRepository,
                           SavedPropertyRepository savedRepository,
                           UniversityService universityService,
                           FileStorageService fileStorageService,
                           AppProperties properties) {
        this.propertyRepository = propertyRepository;
        this.imageRepository = imageRepository;
        this.savedRepository = savedRepository;
        this.universityService = universityService;
        this.fileStorageService = fileStorageService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<PropertySummaryDto> search(ListingStatus status,
                                                    String q,
                                                    UUID universityId,
                                                    BigDecimal minRent,
                                                    BigDecimal maxRent,
                                                    FurnishedStatus furnished,
                                                    PropertyType propertyType,
                                                    Integer minBedrooms,
                                                    Double maxDistanceKm,
                                                    UUID landlordId,
                                                    String sort,
                                                    int page,
                                                    int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Sort ordering = switch (sort == null ? "newest" : sort) {
            case "rent_asc" -> Sort.by(Sort.Direction.ASC, "rentAmount");
            case "rent_desc" -> Sort.by(Sort.Direction.DESC, "rentAmount");
            case "distance" -> Sort.by(Sort.Direction.ASC, "distanceToCampusKm");
            case "popular" -> Sort.by(Sort.Direction.DESC, "viewCount");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        Page<Property> result = propertyRepository.findAll(
                PropertySpecifications.withFilters(status, q, universityId, minRent, maxRent,
                        furnished, propertyType, minBedrooms, maxDistanceKm, landlordId),
                PageRequest.of(safePage, safeSize, ordering));
        List<PropertySummaryDto> content = result.getContent().stream().map(DtoMapper::summary).toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<PropertySummaryDto> featured() {
        return propertyRepository.findTop8ByStatusOrderByCreatedAtDesc(ListingStatus.APPROVED)
                .stream().map(DtoMapper::summary).toList();
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @Transactional
    public PropertyDto getDetail(User requester, UUID id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));

        boolean owner = requester != null && property.getLandlord().getId().equals(requester.getId());
        boolean admin = requester != null && requester.getRole() == UserRole.ADMIN
                && requester.isAdminVerified();
        boolean visible = property.getStatus() == ListingStatus.APPROVED || owner || admin;
        if (!visible) {
            throw new NotFoundException("That listing could not be found.");
        }

        if (!owner) {
            propertyRepository.incrementViewCount(id);
            property.setViewCount(property.getViewCount() + 1);
        }

        boolean saved = requester != null
                && savedRepository.existsByUserIdAndPropertyId(requester.getId(), id);
        boolean includeContact = requester != null;
        return DtoMapper.property(property, saved, includeContact);
    }

    // ------------------------------------------------------------------
    // Landlord management
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<PropertySummaryDto> mine(User landlord, int page, int size) {
        if (landlord.getRole() != UserRole.LANDLORD) {
            throw new ForbiddenException("Only landlord accounts can manage listings.");
        }
        Page<Property> result = propertyRepository.findByLandlordId(landlord.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        List<PropertySummaryDto> content = result.getContent().stream().map(DtoMapper::summary).toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public PropertyDto create(User landlord, PropertyRequest request) {
        requireActiveLandlord(landlord);
        University university = universityService.requireActive(request.universityId());

        Property property = new Property();
        property.setLandlord(landlord);
        property.setStatus(ListingStatus.PENDING_APPROVAL);
        apply(property, request, university);
        propertyRepository.save(property);
        return DtoMapper.property(property, false, true);
    }

    @Transactional
    public PropertyDto update(User landlord, UUID id, PropertyRequest request) {
        requireActiveLandlord(landlord);
        Property property = propertyRepository.findByIdAndLandlordId(id, landlord.getId())
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));
        if (property.getStatus() == ListingStatus.REMOVED) {
            throw new BadRequestException("Restore this listing before editing it.");
        }
        University university = universityService.requireActive(request.universityId());
        apply(property, request, university);

        // An approved listing goes back for review after an edit.
        property.setStatus(ListingStatus.PENDING_APPROVAL);
        property.setAdminNote(null);
        property.setReviewedAt(null);
        property.setReviewedBy(null);
        propertyRepository.save(property);
        return DtoMapper.property(property, false, true);
    }

    @Transactional
    public void remove(User landlord, UUID id) {
        Property property = propertyRepository.findByIdAndLandlordId(id, landlord.getId())
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));
        if (property.getStatus() == ListingStatus.REMOVED) {
            return;
        }
        property.setStatus(ListingStatus.REMOVED);
        propertyRepository.save(property);
    }

    @Transactional
    public PropertyDto restore(User landlord, UUID id) {
        requireActiveLandlord(landlord);
        Property property = propertyRepository.findByIdAndLandlordId(id, landlord.getId())
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));
        if (property.getStatus() != ListingStatus.REMOVED) {
            throw new BadRequestException("This listing is already active.");
        }
        property.setStatus(ListingStatus.PENDING_APPROVAL);
        property.setAdminNote(null);
        propertyRepository.save(property);
        return DtoMapper.property(property, false, true);
    }

    // ------------------------------------------------------------------
    // Images
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PropertyImageDto> images(UUID propertyId) {
        return imageRepository.findByPropertyIdOrderBySortOrderAsc(propertyId)
                .stream().map(DtoMapper::image).toList();
    }

    @Transactional
    public List<PropertyImageDto> addImage(User landlord, UUID propertyId, MultipartFile file) {
        requireActiveLandlord(landlord);
        Property property = propertyRepository.findByIdAndLandlordId(propertyId, landlord.getId())
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));

        long existing = imageRepository.countByPropertyId(propertyId);
        if (existing >= properties.getUpload().getMaxImagesPerProperty()) {
            throw new BadRequestException("You can upload up to "
                    + properties.getUpload().getMaxImagesPerProperty() + " photos per listing.");
        }

        FileStorageService.StoredFile stored = fileStorageService.storePropertyImage(file);
        PropertyImage image = new PropertyImage();
        image.setProperty(property);
        image.setFileName(stored.fileName());
        image.setOriginalName(stored.originalName());
        image.setContentType(stored.contentType());
        image.setFileSize(stored.size());
        image.setCover(existing == 0);
        image.setSortOrder((int) existing);
        imageRepository.save(image);
        return images(propertyId);
    }

    @Transactional
    public List<PropertyImageDto> setCover(User landlord, UUID propertyId, UUID imageId) {
        Property property = propertyRepository.findByIdAndLandlordId(propertyId, landlord.getId())
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));
        imageRepository.findByIdAndPropertyId(imageId, propertyId)
                .orElseThrow(() -> new NotFoundException("That photo could not be found."));
        for (PropertyImage image : property.getImages()) {
            image.setCover(image.getId().equals(imageId));
        }
        propertyRepository.save(property);
        return images(propertyId);
    }

    @Transactional
    public List<PropertyImageDto> deleteImage(User landlord, UUID propertyId, UUID imageId) {
        Property property = propertyRepository.findByIdAndLandlordId(propertyId, landlord.getId())
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));
        PropertyImage image = imageRepository.findByIdAndPropertyId(imageId, propertyId)
                .orElseThrow(() -> new NotFoundException("That photo could not be found."));

        property.getImages().removeIf(candidate -> candidate.getId().equals(imageId));
        fileStorageService.deletePropertyImage(image.getFileName());

        // Promote another photo to the cover and keep ordering tidy.
        List<PropertyImage> remaining = property.getImages().stream()
                .sorted(Comparator.comparingInt(PropertyImage::getSortOrder))
                .toList();
        for (int index = 0; index < remaining.size(); index++) {
            PropertyImage candidate = remaining.get(index);
            candidate.setSortOrder(index);
            candidate.setCover(index == 0);
        }
        propertyRepository.save(property);
        return images(propertyId);
    }

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    @Transactional
    public boolean toggleSaved(User student, UUID propertyId) {
        if (student.getRole() != UserRole.STUDENT) {
            throw new ForbiddenException("Only student accounts can save listings.");
        }
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));
        if (property.getStatus() != ListingStatus.APPROVED) {
            throw new BadRequestException("Only published listings can be saved.");
        }

        var existing = savedRepository.findByUserIdAndPropertyId(student.getId(), propertyId);
        if (existing.isPresent()) {
            savedRepository.delete(existing.get());
            return false;
        }
        SavedProperty saved = new SavedProperty();
        saved.setUser(student);
        saved.setProperty(property);
        savedRepository.save(saved);
        return true;
    }

    @Transactional(readOnly = true)
    public List<UUID> savedIds(User student) {
        requireStudentAccount(student);
        return savedRepository.findByUserIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(saved -> saved.getProperty().getId())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PropertySummaryDto> savedProperties(User student) {
        requireStudentAccount(student);
        return savedRepository.findByUserIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(SavedProperty::getProperty)
                .filter(property -> property.getStatus() == ListingStatus.APPROVED)
                .map(DtoMapper::summary)
                .toList();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void requireStudentAccount(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new ForbiddenException("Only student accounts can save listings.");
        }
    }

    private void requireActiveLandlord(User landlord) {
        if (landlord.getRole() != UserRole.LANDLORD) {
            throw new ForbiddenException("Only landlord accounts can manage listings.");
        }
        if (landlord.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("LANDLORD_NOT_APPROVED",
                    "Your landlord account must be approved by an administrator before you can publish listings.");
        }
    }

    private void apply(Property property, PropertyRequest request, University university) {
        property.setUniversity(university);
        property.setTitle(request.title().trim());
        property.setDescription(request.description().trim());
        property.setPropertyType(request.propertyType());
        property.setRentAmount(request.rentAmount());
        property.setNegotiable(request.negotiable());
        property.setUtilitiesIncluded(request.utilitiesIncluded());
        property.setUtilitiesDescription(trimToNull(request.utilitiesDescription()));
        property.setFurnished(request.furnished());
        property.setBedrooms(request.bedrooms());
        property.setBathrooms(request.bathrooms());
        property.setAddress(request.address().trim());
        property.setCity(request.city().trim());
        property.setRegion(trimToNull(request.region()));
        property.setLatitude(request.latitude());
        property.setLongitude(request.longitude());
        property.setMoveInDate(request.moveInDate());
        property.setLeaseTerms(trimToNull(request.leaseTerms()));
        property.setAmenities(cleanAmenities(request.amenities()));
        property.setDistanceToCampusKm(GeoUtils.roundKm(GeoUtils.haversineKm(
                request.latitude(), request.longitude(),
                university.getLatitude(), university.getLongitude())));
    }

    private Set<String> cleanAmenities(List<String> amenities) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (amenities == null) {
            return cleaned;
        }
        for (String amenity : amenities) {
            if (amenity == null) {
                continue;
            }
            String trimmed = amenity.trim();
            if (!trimmed.isEmpty() && cleaned.size() < 20) {
                cleaned.add(trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed);
            }
        }
        return cleaned;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
