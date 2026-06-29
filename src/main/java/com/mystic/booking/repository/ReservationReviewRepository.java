package com.mystic.booking.repository;

import com.mystic.booking.entity.ReservationReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationReviewRepository extends JpaRepository<ReservationReviewEntity, Long> {
}
