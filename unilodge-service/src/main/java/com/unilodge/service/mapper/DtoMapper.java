package com.unilodge.service.mapper;

import com.unilodge.common.dto.*;
import com.unilodge.persistence.entity.*;

import java.util.Comparator;
import java.util.List;

/** Converts persistence entities into API representations. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static UniversityDto university(University university) {
        return new UniversityDto(
                university.getId(),
                university.getName(),
                university.getShortName(),
                university.getCity(),
                university.getRegion(),
                university.getLatitude(),
                university.getLongitude(),
                university.isActive());
    }

    public static String avatarUrl(User user) {
        return user.getAvatarFile() == null ? null : "/api/files/avatar/" + user.getId();
    }

    public static AuthorDto author(User user) {
        return new AuthorDto(user.getId(), user.getFullName(), avatarUrl(user), user.getRole());
    }

    public static UserResponse user(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getUniversity() == null ? null : university(user.getUniversity()),
                avatarUrl(user),
                user.isEmailVerified(),
                user.isAdminVerified(),
                user.isOriginalAdmin(),
                user.getBusinessName(),
                user.getAdminNote(),
                user.getCreatedAt());
    }

    public static AdminUserDto adminUser(User user) {
        return new AdminUserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.isAdminVerified(),
                user.isOriginalAdmin(),
                user.getUniversity() == null ? null : user.getUniversity().getName(),
                user.getBusinessName(),
                user.getAdminNote(),
                user.getCreatedAt());
    }

    public static AdminInvitationDto invitation(AdminInvitation invitation) {
        return new AdminInvitationDto(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getStatus(),
                invitation.getInvitedBy() == null ? "System" : invitation.getInvitedBy().getFullName(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt());
    }

    public static PropertyImageDto image(PropertyImage image) {
        return new PropertyImageDto(
                image.getId(),
                "/api/files/property/" + image.getId(),
                image.isCover(),
                image.getSortOrder());
    }

    private static PropertyImage coverImage(Property property) {
        return property.getImages().stream()
                .filter(PropertyImage::isCover)
                .findFirst()
                .orElseGet(() -> property.getImages().stream()
                        .min(Comparator.comparingInt(PropertyImage::getSortOrder))
                        .orElse(null));
    }

    public static PropertySummaryDto summary(Property property) {
        PropertyImage cover = coverImage(property);
        return new PropertySummaryDto(
                property.getId(),
                property.getTitle(),
                property.getPropertyType(),
                property.getRentAmount(),
                property.getFurnished(),
                property.getBedrooms(),
                property.getBathrooms(),
                property.getDistanceToCampusKm(),
                property.getCity(),
                property.getUniversity() == null ? null : property.getUniversity().getName(),
                cover == null ? null : "/api/files/property/" + cover.getId(),
                property.getMoveInDate(),
                property.getStatus(),
                property.getCreatedAt());
    }

    public static PropertyDto property(Property property, boolean saved, boolean includeContact) {
        User landlord = property.getLandlord();
        LandlordContactDto contact = new LandlordContactDto(
                landlord.getId(),
                landlord.getFullName(),
                landlord.getBusinessName(),
                includeContact ? landlord.getEmail() : null,
                includeContact ? landlord.getPhone() : null,
                avatarUrl(landlord));
        PropertyImage cover = coverImage(property);
        List<PropertyImageDto> images = property.getImages().stream()
                .sorted(Comparator.comparingInt(PropertyImage::getSortOrder))
                .map(DtoMapper::image)
                .toList();
        return new PropertyDto(
                property.getId(),
                property.getUniversity() == null ? null : property.getUniversity().getId(),
                property.getUniversity() == null ? null : property.getUniversity().getName(),
                property.getTitle(),
                property.getDescription(),
                property.getPropertyType(),
                property.getRentAmount(),
                property.isNegotiable(),
                property.isUtilitiesIncluded(),
                property.getUtilitiesDescription(),
                property.getFurnished(),
                property.getBedrooms(),
                property.getBathrooms(),
                property.getAddress(),
                property.getCity(),
                property.getRegion(),
                property.getLatitude(),
                property.getLongitude(),
                property.getDistanceToCampusKm(),
                property.getMoveInDate(),
                property.getLeaseTerms(),
                List.copyOf(property.getAmenities()),
                property.getStatus(),
                property.getAdminNote(),
                images,
                cover == null ? null : "/api/files/property/" + cover.getId(),
                contact,
                saved,
                property.getViewCount(),
                property.getCreatedAt(),
                property.getUpdatedAt());
    }

    public static StudentPostDto post(StudentPost post, long commentCount, boolean saved) {
        return new StudentPostDto(
                post.getId(),
                post.getTitle(),
                post.getBody(),
                post.getPreferredLocation(),
                post.getBudgetMax(),
                post.getRoommatesNeeded(),
                post.getStatus(),
                post.getAdminNote(),
                post.getUniversity() == null ? null : post.getUniversity().getId(),
                post.getUniversity() == null ? null : post.getUniversity().getName(),
                author(post.getAuthor()),
                commentCount,
                saved,
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    public static CommentDto comment(Comment comment) {
        return new CommentDto(
                comment.getId(),
                comment.getBody(),
                author(comment.getAuthor()),
                comment.getCreatedAt(),
                comment.getStatus());
    }
}
