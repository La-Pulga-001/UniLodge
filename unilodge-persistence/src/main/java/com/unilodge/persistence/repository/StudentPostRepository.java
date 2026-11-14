package com.unilodge.persistence.repository;

import com.unilodge.common.enums.ModerationStatus;
import com.unilodge.persistence.entity.StudentPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentPostRepository extends JpaRepository<StudentPost, UUID>, JpaSpecificationExecutor<StudentPost> {

    Page<StudentPost> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    Optional<StudentPost> findByIdAndAuthorId(UUID id, UUID authorId);

    long countByStatus(ModerationStatus status);

    long countByAuthorId(UUID authorId);

    List<StudentPost> findTop5ByStatusOrderByCreatedAtDesc(ModerationStatus status);

    long countByUniversityId(UUID universityId);
}
