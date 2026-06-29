package com.mystic.booking.dto;

import com.mystic.booking.repository.TopUsedRoomProjection;

public record TopUsedRoomResponse(
        Long roomId,
        String roomName,
        long reservationCount,
        long totalReservedMinutes
) {
    public static TopUsedRoomResponse from(TopUsedRoomProjection p) {
        return new TopUsedRoomResponse(
                p.getRoomId(),
                p.getRoomName(),
                p.getReservationCount(),
                p.getTotalMinutes());
    }
}
