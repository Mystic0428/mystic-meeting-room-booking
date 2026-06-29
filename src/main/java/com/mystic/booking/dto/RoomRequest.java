package com.mystic.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoomRequest(

        @NotBlank(message = "name is required")
        String name,

        @NotNull(message = "capacity is required")
        @Min(value = 1, message = "capacity must be at least 1")
        Integer capacity,

        String floor,

        String location
) {
}
