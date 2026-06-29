package com.mystic.booking.dto;

import com.mystic.booking.enums.ReviewAction;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(

        @NotNull(message = "reviewerId is required")
        Long reviewerId,

        @NotNull(message = "action is required")
        ReviewAction action,

        String comment
) {
}
