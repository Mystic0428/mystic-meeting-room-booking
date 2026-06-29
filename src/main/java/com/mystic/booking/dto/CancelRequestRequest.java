package com.mystic.booking.dto;

import jakarta.validation.constraints.NotNull;

public record CancelRequestRequest(

        @NotNull(message = "userId is required")
        Long userId,

        String reason
) {
}
