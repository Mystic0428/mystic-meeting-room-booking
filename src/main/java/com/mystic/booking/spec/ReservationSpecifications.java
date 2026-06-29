package com.mystic.booking.spec;

import com.mystic.booking.dto.ReservationSearchCriteria;
import com.mystic.booking.entity.ReservationEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 動態組裝 overview 的查詢條件:只有「有給」的條件才加入 predicate。
 */
public final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    public static Specification<ReservationEntity> withCriteria(ReservationSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (c.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), c.dateFrom().atStartOfDay()));
            }
            if (c.dateTo() != null) {
                // 含整個 dateTo 當天 → 用「< 隔天 00:00」
                predicates.add(cb.lessThan(root.get("startTime"), c.dateTo().plusDays(1).atStartOfDay()));
            }
            if (c.roomId() != null) {
                // room.id 是 FK,不會產生 join
                predicates.add(cb.equal(root.get("room").get("id"), c.roomId()));
            }
            if (c.roomName() != null && !c.roomName().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("room").get("name")),
                        "%" + c.roomName().toLowerCase() + "%"));
            }
            if (c.username() != null && !c.username().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("user").get("username")),
                        "%" + c.username().toLowerCase() + "%"));
            }
            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
