package com.unilodge.persistence.specification;

import com.unilodge.common.enums.FurnishedStatus;
import com.unilodge.common.enums.ListingStatus;
import com.unilodge.common.enums.PropertyType;
import com.unilodge.persistence.entity.Property;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Composable filters for the property search endpoint. */
public final class PropertySpecifications {

    private PropertySpecifications() {
    }

    public static Specification<Property> withFilters(
            ListingStatus status,
            String q,
            UUID universityId,
            BigDecimal minRent,
            BigDecimal maxRent,
            FurnishedStatus furnished,
            PropertyType propertyType,
            Integer minBedrooms,
            Double maxDistanceKm,
            UUID landlordId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (universityId != null) {
                predicates.add(cb.equal(root.get("university").get("id"), universityId));
            }
            if (minRent != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rentAmount"), minRent));
            }
            if (maxRent != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("rentAmount"), maxRent));
            }
            if (furnished != null) {
                predicates.add(cb.equal(root.get("furnished"), furnished));
            }
            if (propertyType != null) {
                predicates.add(cb.equal(root.get("propertyType"), propertyType));
            }
            if (minBedrooms != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bedrooms"), minBedrooms));
            }
            if (maxDistanceKm != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("distanceToCampusKm"), maxDistanceKm));
            }
            if (landlordId != null) {
                predicates.add(cb.equal(root.get("landlord").get("id"), landlordId));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("city")), like),
                        cb.like(cb.lower(root.get("address")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
