package com.ufc.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record GiveCoinsDTO(
    @NotBlank String username,
    @Positive int amount
) {}
