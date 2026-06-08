package com.ufc.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupDTO(
    @NotBlank(message = "username is required")
    @Size(min = 3, max = 30, message = "username must be 3-30 characters")
    @Pattern(
        regexp = "^[A-Za-z0-9_]+$",
        message = "username may only contain letters, numbers, and underscores"
    )
    String username,

    // bcrypt only hashes the first 72 bytes, so cap the length explicitly.
    @NotBlank(message = "password is required")
    @Size(min = 8, max = 72, message = "password must be at least 8 characters")
    String password
) {}
