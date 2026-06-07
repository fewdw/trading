package com.ufc.server.dto;

import java.util.List;

public record OrderbookDto(List<PriceLevel> bids, List<PriceLevel> asks) {
    public record PriceLevel(long price, long quantity) {}
}
