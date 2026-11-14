package com.unilodge.persistence.repository;

import com.unilodge.persistence.entity.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, UUID> {

    List<PropertyImage> findByPropertyIdOrderBySortOrderAsc(UUID propertyId);

    Optional<PropertyImage> findByIdAndPropertyId(UUID id, UUID propertyId);

    Optional<PropertyImage> findFirstByPropertyIdOrderBySortOrderAsc(UUID propertyId);

    long countByPropertyId(UUID propertyId);
}
