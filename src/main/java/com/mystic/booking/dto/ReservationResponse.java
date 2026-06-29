package com.mystic.booking.dto;

import com.mystic.booking.entity.ReservationEntity;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        String roomName,
        String username,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String subject,
        String status
) {
    public static ReservationResponse from(ReservationEntity reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getRoom().getName(),       // 攤平:room.name → roomName
                reservation.getUser().getUsername(),   // 攤平:user.username → username
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getSubject(),
                reservation.getStatus().name().toLowerCase()  // PROCESSING → "processing"
        );
    }
}
