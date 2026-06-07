package com.ufc.server.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailDTO(@NotBlank(message = "token is required") String token) {}
