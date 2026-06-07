package com.ufc.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordDTO(
    @NotBlank(message = "token is required") String token,

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 72, message = "password must be at least 8 characters")
    String newPassword
) {}
