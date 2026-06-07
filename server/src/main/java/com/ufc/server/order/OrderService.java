package com.ufc.server.order;

import com.ufc.server.dto.OrderDto;
import com.ufc.server.dto.PlaceOrderDTO;
import com.ufc.server.holding.Holding;
import com.ufc.server.holding.HoldingRepository;
import com.ufc.server.ranking.Fighter;
import com.ufc.server.ranking.FighterRepository;
import com.ufc.server.ranking.Status;
import com.ufc.server.trade.Trade;
import com.ufc.server.trade.TradeRepository;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The matching engine. A single entry point ({@link #placeOrder}) handles both
 * buys and sells: it reserves the user's coins/shares, walks the opposite side
 * of the book in price/time priority, and records the resulting trades.
 *
 * <p>All amounts are in sub-units (1 coin = 100). Concurrency is handled by the
 * {@code @Version} columns on {@link User}/{@link Holding}: a competing fill
 * triggers an {@code OptimisticLockingFailureException} and the whole
 * transaction rolls back, so the caller simply retries.
 *
 * <p><b>Self-trade prevention:</b> a user never trades against their own resting
 * orders. {@link #isSelf} is the single check, used by both the matcher and the
 * market-buy cost estimate so they can't drift apart, and {@link #executeTrade}
 * asserts the invariant as a backstop (a self-trade would corrupt that user's
 * balances and holding, since buyer and seller would be the same entity).
 */
@Service
public class OrderService {

    private final FighterRepository fighterRepository;
    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    public OrderService(
        FighterRepository fighterRepository,
        UserRepository userRepository,
        HoldingRepository holdingRepository,
        OrderRepository orderRepository,
        TradeRepository tradeRepository
    ) {
        this.fighterRepository = fighterRepository;
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
    }

    // ------------------------------------------------------------------
    // POST /api/orders
    // ------------------------------------------------------------------

    @Transactional
    public OrderDto placeOrder(User caller, PlaceOrderDTO dto) {
        User actor = requireActor(caller);
        Fighter fighter = requireTradableFighter(dto.fighterId());

        OrderType type = dto.type();
        Order order = new Order();
        order.setUser(actor);
        order.setFighter(fighter);
        order.setSide(dto.side());
        order.setType(type);
        // @ValidLimitPrice guarantees a limit price is present iff LIMIT.
        order.setLimitPrice(type == OrderType.LIMIT ? dto.limitPrice() : null);
        order.setQuantity(dto.quantity());
        order.setFilledQuantity(0);
        order.setStatus(OrderStatus.OPEN);

        // 1. Reserve funds (buy) or shares (sell) up front so nothing can be
        //    double-spent while we match. Returns coins reserved for this order.
        long reserved = reserve(actor, fighter, order);

        // 2. Persist before matching so trades can reference the order id.
        order = orderRepository.save(order);

        // 3. Walk the opposite side of the book in price/time priority.
        reserved = match(actor, fighter, order, reserved);

        // 4. Settle whatever did not fill.
        settleRemainder(actor, fighter, order, reserved);

        return OrderDto.from(order);
    }

    /** Reserve coins (BUY) or shares (SELL); returns the coins reserved for this order. */
    private long reserve(User actor, Fighter fighter, Order order) {
        if (order.getSide() == OrderSide.SELL) {
            Holding holding = holdingRepository
                .findByUserAndFighter(actor, fighter)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "insufficient shares"
                    )
                );
            long free = holding.getQuantity() - holding.getReservedQuantity();
            if (free < order.getQuantity()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "insufficient shares"
                );
            }
            holding.setReservedQuantity(
                holding.getReservedQuantity() + order.getQuantity()
            );
            return 0;
        }

        // BUY: reserve coins. A limit cost is known exactly; a market cost is
        // estimated by pre-walking the asks (capped at what the user can afford).
        long budget = order.getType() == OrderType.LIMIT
            ? order.getQuantity() * order.getLimitPrice()
            : estimateMarketBuyCost(actor, fighter, order.getQuantity());

        if (
            order.getType() == OrderType.LIMIT &&
            actor.getAvailableCoins() < budget
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "insufficient funds"
            );
        }
        actor.setAvailableCoins(actor.getAvailableCoins() - budget);
        actor.setReservedCoins(actor.getReservedCoins() + budget);
        return budget;
    }

    /** Match the incoming order against the book; returns coins still reserved for it. */
    private long match(User actor, Fighter fighter, Order order, long reserved) {
        List<Order> book = order.getSide() == OrderSide.BUY
            ? orderRepository.findOpenAsks(fighter)
            : orderRepository.findOpenBids(fighter);

        long remaining = order.getQuantity();
        for (Order resting : book) {
            if (remaining <= 0) break;
            if (isSelf(resting, actor)) continue; // self-trade prevention
            long restingRemaining =
                resting.getQuantity() - resting.getFilledQuantity();
            if (restingRemaining <= 0) continue;

            long price = resting.getLimitPrice(); // resting orders are always LIMIT
            if (order.getType() == OrderType.LIMIT) {
                boolean crosses = order.getSide() == OrderSide.BUY
                    ? order.getLimitPrice() >= price
                    : order.getLimitPrice() <= price;
                if (!crosses) break; // book is price-sorted; nothing better follows
            }

            long fillQty = Math.min(remaining, restingRemaining);
            if (order.getSide() == OrderSide.BUY) {
                fillQty = Math.min(fillQty, reserved / price); // never overspend
                if (fillQty <= 0) break;
            }

            executeTrade(order, resting, fillQty, price, fighter);

            if (order.getSide() == OrderSide.BUY) {
                reserved -= order.getType() == OrderType.LIMIT
                    ? fillQty * order.getLimitPrice()
                    : fillQty * price;
            }
            remaining -= fillQty;
        }
        return reserved;
    }

    /** Finalise status and release any reservation a market order can't keep. */
    private void settleRemainder(
        User actor,
        Fighter fighter,
        Order order,
        long reserved
    ) {
        long filled = order.getFilledQuantity();
        long unfilled = order.getQuantity() - filled;

        if (order.getType() == OrderType.MARKET && unfilled > 0) {
            // Market orders never rest: hand back whatever is still reserved.
            if (order.getSide() == OrderSide.BUY) {
                actor.setReservedCoins(actor.getReservedCoins() - reserved);
                actor.setAvailableCoins(actor.getAvailableCoins() + reserved);
            } else {
                Holding holding = holdingRepository
                    .findByUserAndFighter(actor, fighter)
                    .orElseThrow();
                holding.setReservedQuantity(
                    holding.getReservedQuantity() - unfilled
                );
            }
        }

        if (filled >= order.getQuantity()) {
            order.setStatus(OrderStatus.FILLED);
        } else if (order.getType() == OrderType.MARKET) {
            order.setStatus(OrderStatus.CANCELLED); // partial market fill, rest dropped
        } else {
            order.setStatus(filled > 0 ? OrderStatus.PARTIAL : OrderStatus.OPEN);
        }
        // A resting LIMIT remainder keeps its reservation in place.
    }

    /** Move coins and shares for one match, advance both orders, record the trade. */
    private void executeTrade(
        Order incoming,
        Order resting,
        long qty,
        long price,
        Fighter fighter
    ) {
        Order buyOrder = incoming.getSide() == OrderSide.BUY ? incoming : resting;
        Order sellOrder = incoming.getSide() == OrderSide.SELL
            ? incoming
            : resting;
        User buyer = buyOrder.getUser();
        User seller = sellOrder.getUser();

        // Backstop: self-trades are filtered out in match()/estimate. If one
        // ever reaches here, buyer and seller are the same entity and the moves
        // below would corrupt their balances and holding, so fail loudly and let
        // the transaction roll back rather than silently mint or burn value.
        if (buyer.getId().equals(seller.getId())) {
            throw new IllegalStateException(
                "self-trade detected between orders " +
                buyOrder.getId() +
                " and " +
                sellOrder.getId()
            );
        }

        long gross = qty * price;

        // Buyer pays from reserved coins...
        buyer.setReservedCoins(buyer.getReservedCoins() - gross);
        // ...and an incoming limit buy that fills below its limit gets the
        // difference back (it reserved at the limit but pays the resting ask).
        if (buyOrder == incoming && incoming.getType() == OrderType.LIMIT) {
            long overage = (incoming.getLimitPrice() - price) * qty;
            if (overage > 0) {
                buyer.setReservedCoins(buyer.getReservedCoins() - overage);
                buyer.setAvailableCoins(buyer.getAvailableCoins() + overage);
            }
        }
        // Seller receives the proceeds.
        seller.setAvailableCoins(seller.getAvailableCoins() + gross);

        // Shares leave the seller (they were reserved when the sell was placed).
        Holding sellerHolding = holdingRepository
            .findByUserAndFighter(seller, fighter)
            .orElseThrow(() ->
                new IllegalStateException("seller holding missing during fill")
            );
        sellerHolding.setQuantity(sellerHolding.getQuantity() - qty);
        sellerHolding.setReservedQuantity(
            sellerHolding.getReservedQuantity() - qty
        );

        // Shares arrive for the buyer; recompute weighted-average cost basis.
        Holding buyerHolding = holdingRepository
            .findByUserAndFighter(buyer, fighter)
            .orElseGet(() -> {
                Holding h = new Holding();
                h.setUser(buyer);
                h.setFighter(fighter);
                h.setQuantity(0);
                h.setReservedQuantity(0);
                h.setAveragePrice(0);
                return h;
            });
        long newQty = buyerHolding.getQuantity() + qty;
        long newBasis =
            buyerHolding.getQuantity() * buyerHolding.getAveragePrice() + gross;
        buyerHolding.setAveragePrice(newQty > 0 ? newBasis / newQty : 0);
        buyerHolding.setQuantity(newQty);
        holdingRepository.save(buyerHolding);

        // Advance both orders.
        buyOrder.setFilledQuantity(buyOrder.getFilledQuantity() + qty);
        sellOrder.setFilledQuantity(sellOrder.getFilledQuantity() + qty);
        updateFillStatus(buyOrder);
        updateFillStatus(sellOrder);

        fighter.setLastPrice(price);

        Trade trade = new Trade();
        trade.setFighter(fighter);
        trade.setBuyOrder(buyOrder);
        trade.setSellOrder(sellOrder);
        trade.setPrice(price);
        trade.setQuantity(qty);
        tradeRepository.save(trade);
    }

    /** Pre-walk the asks to size a market buy's reservation, capped at available coins. */
    private long estimateMarketBuyCost(User actor, Fighter fighter, long wantQty) {
        long available = actor.getAvailableCoins();
        long remaining = wantQty;
        long cost = 0;
        for (Order ask : orderRepository.findOpenAsks(fighter)) {
            if (remaining <= 0) break;
            if (isSelf(ask, actor)) continue; // self-trade prevention
            long askRemaining = ask.getQuantity() - ask.getFilledQuantity();
            if (askRemaining <= 0) continue;
            long price = ask.getLimitPrice();
            long q = Math.min(remaining, askRemaining);
            q = Math.min(q, (available - cost) / price); // cap at affordability
            if (q <= 0) break;
            cost += q * price;
            remaining -= q;
        }
        return cost;
    }

    private void updateFillStatus(Order order) {
        if (order.getFilledQuantity() >= order.getQuantity()) {
            order.setStatus(OrderStatus.FILLED);
        } else if (order.getFilledQuantity() > 0) {
            order.setStatus(OrderStatus.PARTIAL);
        }
    }

    // ------------------------------------------------------------------
    // DELETE /api/orders/{id}
    // ------------------------------------------------------------------

    @Transactional
    public OrderDto cancelOrder(User caller, Long orderId) {
        User actor = requireActor(caller);
        Order order = orderRepository
            .findById(orderId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "order not found"
                )
            );
        if (!order.getUser().getId().equals(actor.getId())) {
            // Don't reveal the existence of other users' orders.
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "order not found"
            );
        }
        if (
            order.getStatus() != OrderStatus.OPEN &&
            order.getStatus() != OrderStatus.PARTIAL
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "order is not open"
            );
        }

        // Release the reservation on the unfilled remainder. Only LIMIT orders
        // ever rest, so a cancellable order always has a limit price.
        long unfilled = order.getQuantity() - order.getFilledQuantity();
        if (order.getSide() == OrderSide.BUY) {
            long release = unfilled * order.getLimitPrice();
            actor.setReservedCoins(actor.getReservedCoins() - release);
            actor.setAvailableCoins(actor.getAvailableCoins() + release);
        } else {
            Holding holding = holdingRepository
                .findByUserAndFighter(actor, order.getFighter())
                .orElseThrow();
            holding.setReservedQuantity(
                holding.getReservedQuantity() - unfilled
            );
        }
        order.setStatus(OrderStatus.CANCELLED);
        return OrderDto.from(order);
    }

    // ------------------------------------------------------------------
    // GET /api/orders  and  GET /api/orders/{id}
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrderDto> listOrders(User caller, String statusFilter) {
        User actor = requireActor(caller);
        Predicate<Order> keep = filterFor(statusFilter);
        return orderRepository
            .findByUser(actor)
            .stream()
            .filter(keep)
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .map(OrderDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(User caller, Long orderId) {
        Order order = orderRepository
            .findById(orderId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "order not found"
                )
            );
        if (!order.getUser().getId().equals(caller.getId())) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "order not found"
            );
        }
        return OrderDto.from(order);
    }

    /** {@code null}/blank -> all; "open" -> still on the book; otherwise an exact status. */
    private Predicate<Order> filterFor(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return o -> true;
        }
        if (statusFilter.equalsIgnoreCase("open")) {
            return o -> OrderRepository.OPEN_STATUSES.contains(o.getStatus());
        }
        OrderStatus wanted;
        try {
            wanted = OrderStatus.valueOf(statusFilter.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "unknown status: " + statusFilter
            );
        }
        return o -> o.getStatus() == wanted;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Whether a resting order belongs to the user placing the incoming order.
     * The one self-trade check shared by the matcher and the cost estimate.
     * Reads the id off the lazy proxy without initialising it.
     */
    private static boolean isSelf(Order resting, User actor) {
        return resting.getUser().getId().equals(actor.getId());
    }

    /** Reload the caller as a managed entity inside the current transaction. */
    private User requireActor(User caller) {
        return userRepository
            .findById(caller.getId())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized"
                )
            );
    }

    private Fighter requireTradableFighter(String fighterId) {
        long id;
        try {
            id = Long.parseLong(fighterId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "invalid fighterId"
            );
        }
        Fighter fighter = fighterRepository
            .findById(id)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "fighter not found: " + fighterId
                )
            );
        if (fighter.getStatus() != Status.ACTIVE) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "fighter is not tradable"
            );
        }
        return fighter;
    }
}
