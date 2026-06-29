package com.mystic.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateReservationRequest(

        @NotNull(message = "roomId is required")
        Long roomId,

        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "endTime is required")
        LocalDateTime endTime,

        @NotBlank(message = "subject is required")
        String subject,

        String purpose,

        @NotNull(message = "attendeeCount is required")
        @Min(value = 1, message = "attendeeCount must be at least 1")
        Integer attendeeCount
) {
}