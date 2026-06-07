package com.ufc.server.dto;

import com.ufc.server.order.OrderSide;
import com.ufc.server.order.OrderType;
import com.ufc.server.validate.ValidLimitPrice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ValidLimitPrice
public record PlaceOrderDTO(
    @NotBlank String fighterId,
    @NotNull OrderSide side,
    @NotNull OrderType type,
    @Positive Long limitPrice,
    @Positive int quantity
) {}
