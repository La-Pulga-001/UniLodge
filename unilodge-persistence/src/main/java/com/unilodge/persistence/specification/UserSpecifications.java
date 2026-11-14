package com.unilodge.persistence.specification;

import com.unilodge.common.enums.UserRole;
import com.unilodge.common.enums.UserStatus;
import com.unilodge.persistence.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Composable filters for administrator account management. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> withFilters(UserRole role, UserStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("phone")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
