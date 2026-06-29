package com.mystic.booking.entity;

import com.mystic.booking.enums.ReservationStatus;
import com.mystic.booking.exception.IllegalStateTransitionException;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "reservations")
public class ReservationEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 500)
    private String purpose;

    @Column(name = "attendee_count", nullable = false)
    private Integer attendeeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    protected ReservationEntity() {
        // JPA 需要一個無參數建構子
    }

    public ReservationEntity(RoomEntity room, UserEntity user,
                             LocalDateTime startTime, LocalDateTime endTime,
                             String subject, String purpose, Integer attendeeCount) {
        this.room = room;
        this.user = user;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subject = subject;
        this.purpose = purpose;
        this.attendeeCount = attendeeCount;
        this.status = ReservationStatus.PROCESSING;   // 初始狀態由 entity 自己決定
    }

    // ===== 狀態機:每個動作自己檢查「現在能不能做這個轉移」 =====

    /** 審核新訂單:核准 */
    public void approve() {
        requireStatus(ReservationStatus.PROCESSING);
        this.status = ReservationStatus.APPROVED;
    }

    /** 審核新訂單:駁回 */
    public void reject() {
        requireStatus(ReservationStatus.PROCESSING);
        this.status = ReservationStatus.REJECTED;
    }

    /** 本人申請退回(只有已核准的才能退回) */
    public void requestCancel(String reason) {
        requireStatus(ReservationStatus.APPROVED);
        this.status = ReservationStatus.CANCEL_REQUESTED;
        this.cancelReason = reason;
    }

    /** 審核退回申請:同意 → 取消 */
    public void approveCancellation() {
        requireStatus(ReservationStatus.CANCEL_REQUESTED);
        this.status = ReservationStatus.CANCELLED;
    }

    /** 審核退回申請:駁回 → 回到已核准 */
    public void rejectCancellation() {
        requireStatus(ReservationStatus.CANCEL_REQUESTED);
        this.status = ReservationStatus.APPROVED;
    }

    private void requireStatus(ReservationStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateTransitionException(
                    "operation requires status " + expected + " but was " + this.status);
        }
    }
}
