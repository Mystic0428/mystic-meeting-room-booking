package com.mystic.booking.dto;

import com.mystic.booking.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequest(

        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        String email,

        String department,

        @NotNull(message = "role is required")
        Role role
) {
}
