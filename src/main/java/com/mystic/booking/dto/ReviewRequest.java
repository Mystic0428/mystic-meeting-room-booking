package com.mystic.booking.dto;

import com.mystic.booking.enums.ReviewAction;
import jakarta.validation.constraints.NotNull;

// 審核:審核者(reviewerId)改由 JWT 取得,不再從 body 帶。
public record ReviewRequest(

        @NotNull(message = "action is required")
        ReviewAction action,

        String comment
) {
}
