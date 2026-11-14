package com.unilodge.persistence.repository;

import com.unilodge.common.enums.ListingStatus;
import com.unilodge.persistence.entity.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

    Page<Property> findByLandlordId(UUID landlordId, Pageable pageable);

    Optional<Property> findByIdAndLandlordId(UUID id, UUID landlordId);

    long countByStatus(ListingStatus status);

    long countByLandlordId(UUID landlordId);

    long countByLandlordIdAndStatus(UUID landlordId, ListingStatus status);

    List<Property> findTop6ByStatusOrderByCreatedAtDesc(ListingStatus status);

    @Transactional
    @Modifying
    @Query("update Property p set p.viewCount = p.viewCount + 1 where p.id = :id")
    void incrementViewCount(@Param("id") UUID id);
}
