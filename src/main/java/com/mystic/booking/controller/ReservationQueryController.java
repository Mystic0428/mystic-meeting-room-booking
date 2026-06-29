package com.mystic.booking.controller;

import com.mystic.booking.dto.MonthlySummaryResponse;
import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.dto.ReservationSearchCriteria;
import com.mystic.booking.dto.TimelineResponse;
import com.mystic.booking.dto.TopUsedRoomResponse;
import com.mystic.booking.enums.ReservationStatus;
import com.mystic.booking.exception.InvalidReservationException;
import com.mystic.booking.service.ReservationQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated   // 啟用方法參數(@RequestParam / @PathVariable)的 Bean Validation
public class ReservationQueryController {

    private final ReservationQueryService reservationQueryService;

    // 預約總覽:多條件篩選 + 分頁 + 排序(page / size / sort 由 Pageable 自動綁)
    @GetMapping("/api/reservations")
    public Page<ReservationResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String roomName,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        ReservationSearchCriteria criteria = new ReservationSearchCriteria(
                dateFrom, dateTo, roomId, roomName, username, parseStatus(status));
        return reservationQueryService.overview(criteria, pageable);
    }

    @GetMapping("/api/rooms/{roomId}/reservations")
    public List<ReservationResponse> findByRoom(@PathVariable Long roomId) {
        return reservationQueryService.findByRoom(roomId);
    }

    // 查某使用者的所有預約(可選 status 篩選)
    @GetMapping("/api/users/{userId}/reservations")
    public List<ReservationResponse> findByUser(@PathVariable Long userId,
                                                @RequestParam(required = false) String status) {
        return reservationQueryService.findByUser(userId, parseStatus(status));
    }

    @GetMapping("/api/reservations/timeline")
    public TimelineResponse timeline(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reservationQueryService.timeline(date);
    }

    @GetMapping("/api/reservations/monthly-summary")
    public MonthlySummaryResponse monthlySummary(
            @RequestParam @Positive int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return reservationQueryService.monthlySummary(year, month);
    }

    @GetMapping("/api/rooms/top-used")
    public List<TopUsedRoomResponse> topUsedRooms(
            @RequestParam @Positive int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return reservationQueryService.topUsedRooms(year, month);
    }

    // 匯出某月預約為 CSV(加分項);回 text/csv + 附件下載
    @GetMapping("/api/reservations/export")
    public ResponseEntity<String> export(
            @RequestParam @Positive int year,
            @RequestParam @Min(1) @Max(12) int month) {
        String csv = reservationQueryService.exportReservationsCsv(year, month);
        String filename = String.format("reservations-%d-%02d.csv", year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    /** 把 query 字串 status(可小寫)轉成 enum;非法值丟 400。 */
    private ReservationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReservationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidReservationException("invalid status: " + status);
        }
    }
}
