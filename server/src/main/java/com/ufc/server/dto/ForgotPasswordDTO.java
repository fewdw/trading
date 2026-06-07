package com.ufc.server.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordDTO(
    @NotBlank(message = "email is required")
    @Email(message = "enter a valid email")
    String email
) {}
