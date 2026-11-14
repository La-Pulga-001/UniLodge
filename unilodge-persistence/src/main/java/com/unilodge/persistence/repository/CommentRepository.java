package com.unilodge.persistence.repository;

import com.unilodge.common.enums.ModerationStatus;
import com.unilodge.persistence.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByPostIdAndStatusOrderByCreatedAtAsc(UUID postId, ModerationStatus status, Pageable pageable);

    Page<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId, Pageable pageable);

    long countByPostIdAndStatus(UUID postId, ModerationStatus status);

    long countByStatus(ModerationStatus status);

    long countByPostId(UUID postId);

    @Query("select c.post.id, count(c) from Comment c where c.post.id in :postIds and c.status = com.unilodge.common.enums.ModerationStatus.VISIBLE group by c.post.id")
    java.util.List<Object[]> countVisibleByPostIds(@Param("postIds") java.util.Collection<UUID> postIds);
}
