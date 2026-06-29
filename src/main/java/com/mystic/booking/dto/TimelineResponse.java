package com.mystic.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TimelineResponse(
        LocalDate date,
        List<RoomTimeline> rooms
) {
    public record RoomTimeline(
            Long roomId,
            String roomName,
            Integer capacity,
            List<TimelineReservation> reservations
    ) {
    }

    public record TimelineReservation(
            Long reservationId,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            String username,
            String subject,
            String status
    ) {
    }
}
