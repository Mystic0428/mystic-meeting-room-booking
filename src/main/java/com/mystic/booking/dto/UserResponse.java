package com.mystic.booking.dto;

import com.mystic.booking.entity.UserEntity;

public record UserResponse(
        Long id,
        String username,
        String email,
        String department,
        String role
) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDepartment(),
                user.getRole().name());
    }
}
