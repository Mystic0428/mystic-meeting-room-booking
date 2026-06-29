package com.mystic.booking.dto;

import com.mystic.booking.enums.ReservationStatus;

import java.time.LocalDate;

/**
 * overview 查詢的選填條件。任一欄位為 null 代表「不以此條件篩選」。
 */
public record ReservationSearchCriteria(
        LocalDate dateFrom,
        LocalDate dateTo,
        Long roomId,
        String roomName,
        String username,
        ReservationStatus status
) {
}
