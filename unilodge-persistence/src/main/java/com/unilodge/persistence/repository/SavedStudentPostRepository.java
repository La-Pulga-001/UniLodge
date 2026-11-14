package com.unilodge.persistence.repository;

import com.unilodge.persistence.entity.SavedStudentPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedStudentPostRepository extends JpaRepository<SavedStudentPost, UUID> {

    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    Optional<SavedStudentPost> findByUserIdAndPostId(UUID userId, UUID postId);

    List<SavedStudentPost> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);
}
