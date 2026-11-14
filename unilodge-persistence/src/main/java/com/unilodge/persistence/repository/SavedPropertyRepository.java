package com.unilodge.persistence.repository;

import com.unilodge.persistence.entity.SavedProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedPropertyRepository extends JpaRepository<SavedProperty, UUID> {

    boolean existsByUserIdAndPropertyId(UUID userId, UUID propertyId);

    Optional<SavedProperty> findByUserIdAndPropertyId(UUID userId, UUID propertyId);

    List<SavedProperty> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByPropertyId(UUID propertyId);

    long countByUserId(UUID userId);
}
