package com.equipmentrental.equipment;

import com.equipmentrental.domain.Equipment;
import com.equipmentrental.equipment.dto.EquipmentFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class EquipmentSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private EquipmentSpecifications() {
    }

    static Specification<Equipment> matching(EquipmentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = "%" + escapeLike(filter.q().strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE));
            }
            if (filter.minRate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dailyRate"), filter.minRate()));
            }
            if (filter.maxRate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dailyRate"), filter.maxRate()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
