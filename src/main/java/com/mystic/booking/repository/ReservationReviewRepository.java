package com.mystic.booking.repository;

import com.mystic.booking.entity.ReservationReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReservationReviewRepository extends JpaRepository<ReservationReviewEntity, Long> {

    // 匯出用:一次撈出多筆預約的審核紀錄(JOIN FETCH reviewer,避免 N+1)
    @Query("""
           SELECT rv FROM ReservationReviewEntity rv
           JOIN FETCH rv.reviewer
           WHERE rv.reservation.id IN :reservationIds
           """)
    List<ReservationReviewEntity> findByReservationIdInWithReviewer(@Param("reservationIds") Collection<Long> reservationIds);
}
