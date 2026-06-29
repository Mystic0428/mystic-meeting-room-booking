package com.mystic.booking.entity;

import com.mystic.booking.enums.ReviewAction;
import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "reservation_reviews")
public class ReservationReviewEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private ReservationEntity reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private UserEntity reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewAction action;

    @Column(length = 500)
    private String comment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected ReservationReviewEntity() {
        // JPA 需要無參數建構子
    }

    public ReservationReviewEntity(ReservationEntity reservation, UserEntity reviewer,
                                   ReviewAction action, String comment) {
        this.reservation = reservation;
        this.reviewer = reviewer;
        this.action = action;
        this.comment = comment;
        this.reviewedAt = LocalDateTime.now();   // 審核時間
    }
}
