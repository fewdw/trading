package com.ufc.server.dto;

import java.time.Instant;

public record TradeDto(long price, long quantity, Instant executedAt) {}
