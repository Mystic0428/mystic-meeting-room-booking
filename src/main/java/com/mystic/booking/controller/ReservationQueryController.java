package com.mystic.booking.controller;

import com.mystic.booking.dto.MonthlySummaryResponse;
import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.dto.ReservationSearchCriteria;
import com.mystic.booking.dto.TimelineResponse;
import com.mystic.booking.dto.TopUsedRoomResponse;
import com.mystic.booking.enums.ReservationStatus;
import com.mystic.booking.exception.InvalidReservationException;
import com.mystic.booking.service.ReservationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "預約查詢 Query", description = "預約總覽、依條件查詢、每日/每月統計與報表匯出")
@RestController
@RequiredArgsConstructor
@Validated   // 啟用方法參數(@RequestParam / @PathVariable)的 Bean Validation
public class ReservationQueryController {

    private final ReservationQueryService reservationQueryService;

    @Operation(summary = "預約總覽",
            description = "多條件篩選(dateFrom/dateTo/roomId/roomName/username/status)+ 分頁排序(page/size/sort)。")
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

    @Operation(summary = "依會議室查所有預約", description = "依 startTime 排序;會議室不存在回 404")
    @GetMapping("/api/rooms/{roomId}/reservations")
    public List<ReservationResponse> findByRoom(@PathVariable Long roomId) {
        return reservationQueryService.findByRoom(roomId);
    }

    @Operation(summary = "依使用者查所有預約", description = "可選 status 篩選,依 startTime 排序;使用者不存在回 404")
    @GetMapping("/api/users/{userId}/reservations")
    public List<ReservationResponse> findByUser(@PathVariable Long userId,
                                                @RequestParam(required = false) String status) {
        return reservationQueryService.findByUser(userId, parseStatus(status));
    }

    @Operation(summary = "每日時段表", description = "某天各啟用中會議室的 approved 預約;沒預約的房回空清單")
    @GetMapping("/api/reservations/timeline")
    public TimelineResponse timeline(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reservationQueryService.timeline(date);
    }

    @Operation(summary = "每月狀態統計", description = "某月各狀態數量(GROUP BY)+ 當月明細")
    @GetMapping("/api/reservations/monthly-summary")
    public MonthlySummaryResponse monthlySummary(
            @RequestParam @Positive int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return reservationQueryService.monthlySummary(year, month);
    }

    @Operation(summary = "使用率最高前三會議室", description = "僅計 approved,依總預約分鐘數排序")
    @GetMapping("/api/rooms/top-used")
    public List<TopUsedRoomResponse> topUsedRooms(
            @RequestParam @Positive int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return reservationQueryService.topUsedRooms(year, month);
    }

    @Operation(summary = "匯出某月預約 CSV(加分項)",
            description = "回 text/csv 附件下載,含審核者/審核時間;加 UTF-8 BOM 讓 Excel 正確顯示中文")
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
