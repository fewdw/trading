package com.ufc.server.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginDTO(
    @NotBlank(message = "username is required") String username,
    @NotBlank(message = "password is required") String password
) {}
