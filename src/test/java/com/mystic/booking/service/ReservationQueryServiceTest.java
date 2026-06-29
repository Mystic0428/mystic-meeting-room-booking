package com.mystic.booking.service;

import com.mystic.booking.dto.MonthlySummaryResponse;
import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.dto.ReservationSearchCriteria;
import com.mystic.booking.dto.TimelineResponse;
import com.mystic.booking.dto.TopUsedRoomResponse;
import com.mystic.booking.entity.ReservationEntity;
import com.mystic.booking.entity.ReservationReviewEntity;
import com.mystic.booking.entity.RoomEntity;
import com.mystic.booking.entity.UserEntity;
import com.mystic.booking.enums.ReservationStatus;
import com.mystic.booking.enums.ReviewAction;
import com.mystic.booking.enums.Role;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.repository.ReservationRepository;
import com.mystic.booking.repository.ReservationReviewRepository;
import com.mystic.booking.repository.RoomRepository;
import com.mystic.booking.repository.TopUsedRoomProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ReservationQueryService 單元測試")
@ExtendWith(MockitoExtension.class)
class ReservationQueryServiceTest {

    @Mock
    ReservationRepository reservationRepository;
    @Mock
    RoomRepository roomRepository;
    @Mock
    ReservationReviewRepository reservationReviewRepository;

    @InjectMocks
    ReservationQueryService queryService;

    @Test
    @DisplayName("overview:用 Specification 查分頁,並把每筆轉成 DTO")
    void overview_shouldReturnMappedPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ReservationEntity> page = new PageImpl<>(List.of(reservation(ReservationStatus.APPROVED)));
        when(reservationRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<ReservationResponse> result = queryService.overview(
                new ReservationSearchCriteria(null, null, null, null, null, null), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("approved");
    }

    @Test
    @DisplayName("findByRoom:房間存在時,回傳該房所有預約的 DTO")
    void findByRoom_shouldReturnReservations_whenRoomExists() {
        when(roomRepository.existsById(1L)).thenReturn(true);
        when(reservationRepository.findByRoomIdWithDetails(1L))
                .thenReturn(List.of(reservation(ReservationStatus.APPROVED)));

        List<ReservationResponse> result = queryService.findByRoom(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findByRoom:房間不存在 → 404")
    void findByRoom_shouldThrowNotFound_whenRoomMissing() {
        when(roomRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> queryService.findByRoom(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("timeline:以房間清單為主軸,沒預約的房得到空 list")
    void timeline_shouldGroupReservationsByRoom() {
        RoomEntity roomA = room(1L, "會議室 A");
        RoomEntity roomB = room(2L, "會議室 B");
        when(roomRepository.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(roomA, roomB));
        // 只有 A 房有一筆 approved
        ReservationEntity res = reservation(ReservationStatus.APPROVED, roomA);
        when(reservationRepository.findApprovedByDateWithUser(eq(ReservationStatus.APPROVED), any(), any()))
                .thenReturn(List.of(res));

        TimelineResponse result = queryService.timeline(LocalDate.of(2099, 1, 1));

        assertThat(result.rooms()).hasSize(2);
        assertThat(result.rooms().get(0).reservations()).hasSize(1);   // A 房
        assertThat(result.rooms().get(1).reservations()).isEmpty();    // B 房沒預約
    }

    @Test
    @DisplayName("monthlySummary:每個狀態先補 0,再用 GROUP BY 的結果覆蓋")
    void monthlySummary_shouldFillZerosThenOverride() {
        // DB 只回 approved=2;其餘狀態應補 0
        when(reservationRepository.countByStatusForMonth(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{ReservationStatus.APPROVED, 2L}));
        when(reservationRepository.findByMonthWithDetails(any(), any()))
                .thenReturn(List.of(reservation(ReservationStatus.APPROVED)));

        MonthlySummaryResponse result = queryService.monthlySummary(2099, 1);

        assertThat(result.summary()).containsEntry("approved", 2L);
        assertThat(result.summary()).containsEntry("processing", 0L);   // 沒出現的補 0
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @DisplayName("topUsedRooms:把 projection 轉成 DTO")
    void topUsedRooms_shouldMapProjection() {
        TopUsedRoomProjection p = mock(TopUsedRoomProjection.class);
        when(p.getRoomId()).thenReturn(1L);
        when(p.getRoomName()).thenReturn("會議室 A");
        when(p.getReservationCount()).thenReturn(5L);
        when(p.getTotalMinutes()).thenReturn(300L);
        when(reservationRepository.findTopUsedRooms(any(), any())).thenReturn(List.of(p));

        List<TopUsedRoomResponse> result = queryService.topUsedRooms(2099, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roomName()).isEqualTo("會議室 A");
        assertThat(result.get(0).totalReservedMinutes()).isEqualTo(300L);
    }

    @Test
    @DisplayName("exportReservationsCsv:輸出表頭 + 每筆預約一列(含最新審核者)")
    void exportReservationsCsv_buildsCsv() {
        ReservationEntity res = reservation(ReservationStatus.APPROVED);   // id=100, 會議室 A, 小明/IT
        ReservationReviewEntity review = new ReservationReviewEntity(res, user(), ReviewAction.APPROVED, "ok");
        when(reservationRepository.findByMonthWithDetails(any(), any())).thenReturn(List.of(res));
        when(reservationReviewRepository.findByReservationIdInWithReviewer(any())).thenReturn(List.of(review));

        String csv = queryService.exportReservationsCsv(2099, 1);

        assertThat(csv).contains("預約編號,會議室,預約人,部門,開始時間,結束時間,狀態,審核者,審核時間");
        assertThat(csv).contains("會議室 A").contains("approved").contains("小明");
        assertThat(csv.strip().split("\n")).hasSize(2);   // 表頭 + 1 筆
    }

    // ---- helpers ----

    private ReservationEntity reservation(ReservationStatus status) {
        return reservation(status, room(1L, "會議室 A"));
    }

    private ReservationEntity reservation(ReservationStatus status, RoomEntity room) {
        ReservationEntity res = new ReservationEntity(room, user(),
                LocalDateTime.of(2099, 1, 1, 10, 0),
                LocalDateTime.of(2099, 1, 1, 11, 0),
                "Subject", "Purpose", 5);
        ReflectionTestUtils.setField(res, "id", 100L);
        ReflectionTestUtils.setField(res, "status", status);
        return res;
    }

    private RoomEntity room(Long id, String name) {
        RoomEntity r = new RoomEntity(name, 10, "1F", "A");
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private UserEntity user() {
        UserEntity u = new UserEntity("小明", "a@example.com", "IT", Role.USER);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }
}
