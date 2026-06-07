package com.ufc.server.dto;

import com.ufc.server.order.Order;
import com.ufc.server.order.OrderSide;
import com.ufc.server.order.OrderStatus;
import com.ufc.server.order.OrderType;
import java.time.Instant;

/**
 * An order as returned to its owner. Build this inside a transaction:
 * {@link #from} touches the lazy fighter association.
 */
public record OrderDto(
    Long id,
    Long fighterId,
    String fighterName,
    OrderSide side,
    OrderType type,
    Long limitPrice,
    long quantity,
    long filledQuantity,
    OrderStatus status,
    Instant createdAt
) {
    public static OrderDto from(Order o) {
        return new OrderDto(
            o.getId(),
            o.getFighter().getId(),
            o.getFighter().getName(),
            o.getSide(),
            o.getType(),
            o.getLimitPrice(),
            o.getQuantity(),
            o.getFilledQuantity(),
            o.getStatus(),
            o.getCreatedAt()
        );
    }
}
