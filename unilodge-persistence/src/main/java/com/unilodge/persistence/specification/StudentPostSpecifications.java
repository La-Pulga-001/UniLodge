package com.unilodge.persistence.specification;

import com.unilodge.common.enums.ModerationStatus;
import com.unilodge.persistence.entity.StudentPost;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Composable filters for the student posts board. */
public final class StudentPostSpecifications {

    private StudentPostSpecifications() {
    }

    public static Specification<StudentPost> withFilters(
            ModerationStatus status, String q, UUID universityId, UUID authorId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (universityId != null) {
                predicates.add(cb.equal(root.get("university").get("id"), universityId));
            }
            if (authorId != null) {
                predicates.add(cb.equal(root.get("author").get("id"), authorId));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("body")), like),
                        cb.like(cb.lower(root.get("preferredLocation")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
