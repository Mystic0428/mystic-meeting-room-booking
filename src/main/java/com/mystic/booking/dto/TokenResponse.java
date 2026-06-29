package com.mystic.booking.dto;

public record TokenResponse(
        String token,
        String tokenType,   // 固定 "Bearer"
        Long userId,
        String role
) {
}
