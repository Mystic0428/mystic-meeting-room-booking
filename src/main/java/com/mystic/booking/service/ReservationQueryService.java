package com.mystic.booking.service;

import com.mystic.booking.dto.MonthlySummaryResponse;
import com.mystic.booking.dto.MonthlySummaryResponse.Item;
import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.dto.ReservationSearchCriteria;
import com.mystic.booking.dto.TimelineResponse;
import com.mystic.booking.dto.TimelineResponse.RoomTimeline;
import com.mystic.booking.dto.TimelineResponse.TimelineReservation;
import com.mystic.booking.dto.TopUsedRoomResponse;
import com.mystic.booking.entity.ReservationEntity;
import com.mystic.booking.entity.RoomEntity;
import com.mystic.booking.enums.ReservationStatus;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.repository.ReservationRepository;
import com.mystic.booking.repository.RoomRepository;
import com.mystic.booking.spec.ReservationSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 預約的「查詢」邏輯,跟負責寫入的 ReservationService 分開(command / query 分離)。
 */
@Service
@RequiredArgsConstructor
public class ReservationQueryService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;

    @Transactional(readOnly = true)
    public Page<ReservationResponse> overview(ReservationSearchCriteria criteria, Pageable pageable) {
        return reservationRepository
                .findAll(ReservationSpecifications.withCriteria(criteria), pageable)
                .map(ReservationResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> findByRoom(Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new ResourceNotFoundException("Room not found: " + roomId);
        }
        return reservationRepository.findByRoomIdWithDetails(roomId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TimelineResponse timeline(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        // 查詢 1:當天所有「啟用中」會議室(依名稱排序)
        List<RoomEntity> rooms = roomRepository.findByIsActiveTrueOrderByNameAsc();

        // 查詢 2:當天所有 approved 預約(JOIN FETCH user;依 startTime 排序)
        List<ReservationEntity> reservations = reservationRepository
                .findApprovedByDateWithUser(ReservationStatus.APPROVED, dayStart, dayEnd);

        // 依 room id 分組(getRoom().getId() 讀的是 FK,不觸發 lazy load)
        Map<Long, List<ReservationEntity>> byRoom = reservations.stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        // 以「房間清單」為主軸組裝 → 當天沒預約的房自然得到空 list
        List<RoomTimeline> roomTimelines = rooms.stream()
                .map(room -> new RoomTimeline(
                        room.getId(),
                        room.getName(),
                        room.getCapacity(),
                        byRoom.getOrDefault(room.getId(), List.of()).stream()
                                .map(this::toTimelineReservation)
                                .toList()))
                .toList();

        return new TimelineResponse(date, roomTimelines);
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse monthlySummary(int year, int month) {
        LocalDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);

        // summary:GROUP BY status(統計交給 DB)。先把每個狀態填 0,確保輸出格式一致
        Map<String, Long> summary = new LinkedHashMap<>();
        for (ReservationStatus status : ReservationStatus.values()) {
            summary.put(status.name().toLowerCase(), 0L);
        }
        for (Object[] row : reservationRepository.countByStatusForMonth(monthStart, monthEnd)) {
            ReservationStatus status = (ReservationStatus) row[0];
            Long count = (Long) row[1];
            summary.put(status.name().toLowerCase(), count);
        }

        // items:當月明細,依 startTime 排序
        List<Item> items = reservationRepository.findByMonthWithDetails(monthStart, monthEnd).stream()
                .map(r -> new Item(
                        r.getId(),
                        r.getRoom().getName(),
                        r.getUser().getUsername(),
                        r.getStartTime(),
                        r.getEndTime(),
                        r.getStatus().name().toLowerCase()))
                .toList();

        return new MonthlySummaryResponse(year, month, summary, items);
    }

    @Transactional(readOnly = true)
    public List<TopUsedRoomResponse> topUsedRooms(int year, int month) {
        LocalDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        return reservationRepository.findTopUsedRooms(monthStart, monthEnd).stream()
                .map(TopUsedRoomResponse::from)
                .toList();
    }

    private TimelineReservation toTimelineReservation(ReservationEntity r) {
        return new TimelineReservation(
                r.getId(),
                r.getStartTime().toLocalTime(),
                r.getEndTime().toLocalTime(),
                r.getUser().getUsername(),
                r.getSubject(),
                r.getStatus().name().toLowerCase());
    }
}
