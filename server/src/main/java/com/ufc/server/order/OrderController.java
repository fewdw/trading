package com.ufc.server.order;

import com.ufc.server.auth.CurrentUserService;
import com.ufc.server.dto.OrderDto;
import com.ufc.server.dto.PlaceOrderDTO;
import com.ufc.server.user.User;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public OrderController(
        OrderService orderService,
        CurrentUserService currentUserService
    ) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    /** Place a buy or sell order; it matches against the book immediately. */
    @PostMapping
    public ResponseEntity<OrderDto> placeOrder(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader,
        @Valid @RequestBody PlaceOrderDTO order
    ) {
        User user = currentUserService.requireUser(authHeader);
        OrderDto placed = orderService.placeOrder(user, order);
        return ResponseEntity.status(HttpStatus.CREATED).body(placed);
    }

    /** Cancel one of your open/partially-filled orders, releasing its reservation. */
    @DeleteMapping("/{orderId}")
    public OrderDto cancelOrder(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader,
        @PathVariable Long orderId
    ) {
        User user = currentUserService.requireUser(authHeader);
        return orderService.cancelOrder(user, orderId);
    }

    /** List your orders, optionally filtered by {@code ?status=open|OPEN|FILLED|...}. */
    @GetMapping
    public List<OrderDto> listOrders(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader,
        @RequestParam(required = false) String status
    ) {
        User user = currentUserService.requireUser(authHeader);
        return orderService.listOrders(user, status);
    }

    /** Fetch one of your orders by id. */
    @GetMapping("/{orderId}")
    public OrderDto getOrder(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader,
        @PathVariable Long orderId
    ) {
        User user = currentUserService.requireUser(authHeader);
        return orderService.getOrder(user, orderId);
    }

    /** A concurrent fill touched the same wallet/holding; the tx rolled back. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleConflict(
        OptimisticLockingFailureException e
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            Map.of("error", "the order book changed, please retry")
        );
    }
}
