package com.mystic.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MonthlySummaryResponse(
        int year,
        int month,
        Map<String, Long> summary,
        List<Item> items
) {
    public record Item(
            Long reservationId,
            String roomName,
            String username,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startTime,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endTime,
            String status
    ) {
    }
}
