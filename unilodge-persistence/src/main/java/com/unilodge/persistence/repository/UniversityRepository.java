package com.unilodge.persistence.repository;

import com.unilodge.persistence.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UniversityRepository extends JpaRepository<University, UUID> {

    Optional<University> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<University> findByActiveTrueOrderByNameAsc();

    List<University> findAllByOrderByNameAsc();
}
