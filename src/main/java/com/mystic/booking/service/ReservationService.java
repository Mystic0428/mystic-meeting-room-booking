package com.mystic.booking.service;

import com.mystic.booking.dto.CancelRequestRequest;
import com.mystic.booking.dto.CreateReservationRequest;
import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.dto.ReviewRequest;
import com.mystic.booking.entity.ReservationEntity;
import com.mystic.booking.entity.ReservationReviewEntity;
import com.mystic.booking.entity.RoomEntity;
import com.mystic.booking.entity.UserEntity;
import com.mystic.booking.enums.ReservationStatus;
import com.mystic.booking.enums.ReviewAction;
import com.mystic.booking.enums.Role;
import com.mystic.booking.exception.ForbiddenException;
import com.mystic.booking.exception.IllegalStateTransitionException;
import com.mystic.booking.exception.InvalidReservationException;
import com.mystic.booking.exception.ReservationConflictException;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.repository.ReservationRepository;
import com.mystic.booking.repository.ReservationReviewRepository;
import com.mystic.booking.repository.RoomRepository;
import com.mystic.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    // 「佔用中」的狀態 = 衝突判斷要算進去的(cancel_requested 仍佔用)
    private static final List<ReservationStatus> OCCUPYING_STATUSES =
            List.of(ReservationStatus.PROCESSING,
                    ReservationStatus.APPROVED,
                    ReservationStatus.CANCEL_REQUESTED);

    private final ReservationRepository reservationRepository;
    private final ReservationReviewRepository reservationReviewRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    /**
     * 建立一筆會議室預約。
     *
     * <p>流程:驗證 room/user 存在 → 檢查業務規則(起訖時間、不可過去、30 分鐘單位、容量)
     * → 應用層衝突檢查 → 寫入(由 DB exclusion constraint 做最後的併發防護)。
     * 整個方法在單一 transaction 內,任一步驟拋出例外都會 rollback。
     *
     * @param request 預約請求,已通過 Bean Validation 的單欄位檢查
     * @return 建立後的預約資訊(攤平 roomName/username,不外露 entity)
     * @throws ResourceNotFoundException    roomId 或 userId 不存在(→ 404)
     * @throws InvalidReservationException   違反業務規則:起訖時間、過去時間、30 分鐘單位、超過容量(→ 400)
     * @throws ReservationConflictException 該時段已被佔用,或併發競態下被 DB constraint 擋下(→ 409)
     */
    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        // 1) room / user 必須存在
        RoomEntity room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + request.roomId()));
        if (!room.isActive()) {
            throw new InvalidReservationException("room is not active: " + request.roomId());
        }
        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));

        // 2) 業務規則(B 群)
        validateTimeRange(request);
        validateCapacity(request, room);

        // 3) 應用層衝突檢查(友善的 409)
        checkConflict(request);

        // 4) 建立(透過受控建構子,status 自動 = PROCESSING)
        ReservationEntity reservation = new ReservationEntity(
                room, user,
                request.startTime(), request.endTime(),
                request.subject(), request.purpose(), request.attendeeCount());

        // 5) 存檔。saveAndFlush 強制現在就 INSERT;
        //    併發競態下 DB exclusion constraint 擋下時,例外會在這裡被接到 → 轉 409
        try {
            ReservationEntity saved = reservationRepository.saveAndFlush(reservation);
            return ReservationResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ReservationConflictException("此會議室在該時段已被預約");
        }
    }

    /**
     * 本人申請退回一筆已核准的預約。
     *
     * @throws ResourceNotFoundException 預約不存在(→ 404)
     * @throws ForbiddenException        申請者不是預約本人(→ 403)
     */
    @Transactional
    public ReservationResponse requestCancellation(Long reservationId, CancelRequestRequest request) {
        ReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        // 授權:只有預約本人可以申請退回(getUser().getId() 讀 FK,不觸發 lazy load)
        if (!reservation.getUser().getId().equals(request.userId())) {
            throw new ForbiddenException("only the owner can request cancellation");
        }

        // domain method 守狀態合法性(只有 APPROVED 能退回);dirty checking 交易結束自動 flush
        reservation.requestCancel(request.reason());
        return ReservationResponse.from(reservation);
    }

    /**
     * REVIEWER / ADMIN 審核:依預約「現在狀態」決定在審「新訂單」還是「退回申請」,並留下審核紀錄。
     *
     * @throws ResourceNotFoundException       預約或審核者不存在(→ 404)
     * @throws ForbiddenException              審核者不是 REVIEWER / ADMIN(→ 403)
     * @throws IllegalStateTransitionException 預約不在可審核狀態(→ 409)
     */
    @Transactional
    public ReservationResponse review(Long reservationId, ReviewRequest request) {
        ReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        UserEntity reviewer = userRepository.findById(request.reviewerId())
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found: " + request.reviewerId()));

        // 授權:只有 REVIEWER / ADMIN 可以審核
        if (!isReviewer(reviewer)) {
            throw new ForbiddenException("only REVIEWER or ADMIN can review");
        }

        // 依現在狀態決定在審訂單還是審退回;domain method 守每個轉移的合法性
        applyReview(reservation, request.action());

        // 留下審核紀錄(審核者、action、意見、審核時間)
        reservationReviewRepository.save(
                new ReservationReviewEntity(reservation, reviewer, request.action(), request.comment()));

        return ReservationResponse.from(reservation);
    }

    private boolean isReviewer(UserEntity user) {
        return user.getRole() == Role.REVIEWER || user.getRole() == Role.ADMIN;
    }

    private void applyReview(ReservationEntity reservation, ReviewAction action) {
        switch (reservation.getStatus()) {
            case PROCESSING -> {                       // 審「新訂單」
                if (action == ReviewAction.APPROVED) {
                    reservation.approve();
                } else {
                    reservation.reject();
                }
            }
            case CANCEL_REQUESTED -> {                 // 審「退回申請」
                if (action == ReviewAction.APPROVED) {
                    reservation.approveCancellation();
                } else {
                    reservation.rejectCancellation();
                }
            }
            default -> throw new IllegalStateTransitionException(
                    "reservation is not in a reviewable state: " + reservation.getStatus());
        }
    }

    private void validateTimeRange(CreateReservationRequest request) {
        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();
        if (!start.isBefore(end)) {
            throw new InvalidReservationException("startTime must be before endTime");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new InvalidReservationException("cannot reserve a past time");
        }
        if (!isHalfHourAligned(start) || !isHalfHourAligned(end)) {
            throw new InvalidReservationException("reservation must be in 30-minute units");
        }
    }

    private boolean isHalfHourAligned(LocalDateTime time) {
        return time.getMinute() % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    private void validateCapacity(CreateReservationRequest request, RoomEntity room) {
        if (request.attendeeCount() > room.getCapacity()) {
            throw new InvalidReservationException(
                    "attendeeCount exceeds room capacity (" + room.getCapacity() + ")");
        }
    }

    private void checkConflict(CreateReservationRequest request) {
        boolean conflict = reservationRepository.existsOverlapping(
                request.roomId(), OCCUPYING_STATUSES,
                request.startTime(), request.endTime());
        if (conflict) {
            throw new ReservationConflictException("此會議室在該時段已被預約");
        }
    }
}
