package com.ufc.server.dto;

import com.ufc.server.ranking.Status;

public record FighterDto(
    Long id,
    String name,
    String photo,
    Status status,
    long lastPrice
) {}
