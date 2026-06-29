package com.mystic.booking.service;

import com.mystic.booking.dto.CancelRequestRequest;
import com.mystic.booking.dto.CreateReservationRequest;
import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.dto.ReviewRequest;
import com.mystic.booking.entity.ReservationEntity;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ReservationService 單元測試")
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    ReservationRepository reservationRepository;
    @Mock
    ReservationReviewRepository reservationReviewRepository;
    @Mock
    RoomRepository roomRepository;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    ReservationService reservationService;

    // ============ create ============

    @Test
    @DisplayName("建立預約:資料合法時成功建立(status = processing)")
    void create_shouldCreateReservation_whenRequestIsValid() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, 10)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.USER)));
        when(reservationRepository.existsOverlapping(anyLong(), any(), any(), any())).thenReturn(false);
        when(reservationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = reservationService.create(validCreateRequest());

        assertThat(response.status()).isEqualTo("processing");
        verify(reservationRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("建立預約:roomId 不存在 → 404")
    void create_shouldThrowNotFound_whenRoomDoesNotExist() {
        when(roomRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(validCreateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("建立預約:userId 不存在 → 404")
    void create_shouldThrowNotFound_whenUserDoesNotExist() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, 10)));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(validCreateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("建立預約:startTime 晚於 endTime → 400")
    void create_shouldThrowInvalid_whenStartTimeNotBeforeEndTime() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, 10)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.USER)));
        CreateReservationRequest req = new CreateReservationRequest(1L, 1L,
                LocalDateTime.of(2099, 1, 1, 11, 0),
                LocalDateTime.of(2099, 1, 1, 10, 0),   // end 在 start 之前
                "S", "P", 5);

        assertThatThrownBy(() -> reservationService.create(req))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    @DisplayName("建立預約:預約過去時間 → 400")
    void create_shouldThrowInvalid_whenReservingPastTime() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, 10)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.USER)));
        CreateReservationRequest req = new CreateReservationRequest(1L, 1L,
                LocalDateTime.of(2000, 1, 1, 10, 0),
                LocalDateTime.of(2000, 1, 1, 11, 0),   // 過去
                "S", "P", 5);

        assertThatThrownBy(() -> reservationService.create(req))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    @DisplayName("建立預約:人數超過會議室容量 → 400")
    void create_shouldThrowInvalid_whenAttendeeCountExceedsCapacity() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, 5)));   // 容量 5
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.USER)));
        CreateReservationRequest req = new CreateReservationRequest(1L, 1L,
                LocalDateTime.of(2099, 1, 1, 10, 0),
                LocalDateTime.of(2099, 1, 1, 11, 0),
                "S", "P", 10);   // 10 > 5

        assertThatThrownBy(() -> reservationService.create(req))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    @DisplayName("建立預約:同會議室同時段衝突 → 409")
    void create_shouldThrowConflict_whenRoomAlreadyBooked() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, 10)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.USER)));
        when(reservationRepository.existsOverlapping(anyLong(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reservationService.create(validCreateRequest()))
                .isInstanceOf(ReservationConflictException.class);
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("建立預約:衝突檢查只把 processing/approved/cancel_requested 視為佔用(含 11、12 會擋;排除 9、10 不擋)")
    void create_shouldCheckConflictAgainstOccupyingStatusesOnly() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, 10)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.USER)));
        when(reservationRepository.existsOverlapping(anyLong(), any(), any(), any())).thenReturn(false);
        when(reservationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        reservationService.create(validCreateRequest());

        // 捕捉 service 傳給 existsOverlapping 的「佔用中狀態」集合,驗證它含哪些、不含哪些
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ReservationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(reservationRepository).existsOverlapping(eq(1L), statuses.capture(), any(), any());
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(
                        ReservationStatus.PROCESSING,
                        ReservationStatus.APPROVED,
                        ReservationStatus.CANCEL_REQUESTED)
                .doesNotContain(ReservationStatus.REJECTED, ReservationStatus.CANCELLED);
    }

    // ============ cancel-request ============

    @Test
    @DisplayName("申請退回:本人退回已核准的預約 → cancel_requested")
    void requestCancellation_shouldSucceed_whenOwnerRequestsApprovedReservation() {
        ReservationEntity res = reservationWithStatus(ReservationStatus.APPROVED, user(1L, Role.USER));
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(res));

        ReservationResponse response =
                reservationService.requestCancellation(100L, new CancelRequestRequest(1L, "會議取消"));

        assertThat(response.status()).isEqualTo("cancel_requested");
    }

    @Test
    @DisplayName("申請退回:非本人申請 → 403")
    void requestCancellation_shouldThrowForbidden_whenUserIsNotOwner() {
        ReservationEntity res = reservationWithStatus(ReservationStatus.APPROVED, user(1L, Role.USER));
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(res));

        assertThatThrownBy(() ->
                reservationService.requestCancellation(100L, new CancelRequestRequest(2L, "x")))
                .isInstanceOf(ForbiddenException.class);
    }

    // ============ review ============

    @Test
    @DisplayName("審核:審核者核准新訂單(processing → approved)")
    void review_shouldApproveBooking_whenReviewerApprovesProcessingReservation() {
        ReservationEntity res = reservationWithStatus(ReservationStatus.PROCESSING, user(1L, Role.USER));
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(res));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.ADMIN)));

        ReservationResponse response =
                reservationService.review(100L, new ReviewRequest(2L, ReviewAction.APPROVED, "核准"));

        assertThat(response.status()).isEqualTo("approved");
        verify(reservationReviewRepository).save(any());
    }

    @Test
    @DisplayName("審核:審核者同意退回(cancel_requested → cancelled)")
    void review_shouldApproveCancellation_whenReviewerApprovesCancelRequest() {
        ReservationEntity res = reservationWithStatus(ReservationStatus.CANCEL_REQUESTED, user(1L, Role.USER));
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(res));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.REVIEWER)));

        ReservationResponse response =
                reservationService.review(100L, new ReviewRequest(2L, ReviewAction.APPROVED, "同意退回"));

        assertThat(response.status()).isEqualTo("cancelled");
    }

    @Test
    @DisplayName("審核:非 REVIEWER / ADMIN 審核 → 403")
    void review_shouldThrowForbidden_whenReviewerIsNotReviewerRole() {
        ReservationEntity res = reservationWithStatus(ReservationStatus.CANCEL_REQUESTED, user(1L, Role.USER));
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(res));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.USER)));

        assertThatThrownBy(() ->
                reservationService.review(100L, new ReviewRequest(1L, ReviewAction.APPROVED, "x")))
                .isInstanceOf(ForbiddenException.class);
        verify(reservationReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("審核:預約不在可審核狀態 → 409")
    void review_shouldThrowIllegalState_whenReservationNotReviewable() {
        ReservationEntity res = reservationWithStatus(ReservationStatus.APPROVED, user(1L, Role.USER));
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(res));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.REVIEWER)));

        assertThatThrownBy(() ->
                reservationService.review(100L, new ReviewRequest(2L, ReviewAction.APPROVED, "x")))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    // ============ helpers ============

    private CreateReservationRequest validCreateRequest() {
        return new CreateReservationRequest(1L, 1L,
                LocalDateTime.of(2099, 1, 1, 10, 0),
                LocalDateTime.of(2099, 1, 1, 11, 0),
                "Subject", "Purpose", 5);
    }

    private UserEntity user(Long id, Role role) {
        UserEntity u = new UserEntity("tester", "t@example.com", "dept", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private RoomEntity room(Long id, int capacity) {
        RoomEntity r = new RoomEntity("Room", capacity, "1F", "A");
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private ReservationEntity reservationWithStatus(ReservationStatus status, UserEntity owner) {
        ReservationEntity res = new ReservationEntity(room(1L, 10), owner,
                LocalDateTime.of(2099, 1, 1, 10, 0),
                LocalDateTime.of(2099, 1, 1, 11, 0),
                "Subject", "Purpose", 5);
        ReflectionTestUtils.setField(res, "status", status);
        return res;
    }
}
