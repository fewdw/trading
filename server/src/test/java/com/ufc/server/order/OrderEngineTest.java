package com.ufc.server.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ufc.server.dto.OrderDto;
import com.ufc.server.dto.PlaceOrderDTO;
import com.ufc.server.holding.Holding;
import com.ufc.server.holding.HoldingRepository;
import com.ufc.server.ranking.Fighter;
import com.ufc.server.ranking.FighterRepository;
import com.ufc.server.ranking.Status;
import com.ufc.server.support.PostgresTestcontainer;
import com.ufc.server.trade.Trade;
import com.ufc.server.trade.TradeRepository;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Property/invariant tests for the matching engine, run against a real Postgres
 * (Testcontainers). Rather than only checking outputs, each test asserts the
 * <em>invariants a financial system must never violate</em>:
 *
 * <ul>
 *   <li><b>Conservation of shares</b> — trading only moves shares between users;
 *       the total in circulation never changes.</li>
 *   <li><b>Conservation of money</b> — coins are never minted or burned; the sum
 *       of every actor's {@code available + reserved} is constant across a trade
 *       (this is what catches the subtle limit-buy "overage refund" bug class).</li>
 *   <li><b>No self-trades</b> — a user never fills against their own resting
 *       order.</li>
 *   <li><b>Reservations are exact</b> — cancelling releases precisely what was
 *       held; partial fills leave the remainder reserved.</li>
 *   <li><b>Price/time priority</b> — best price first, oldest-at-a-price first.</li>
 * </ul>
 *
 * <p>The engine is constructed directly (with a no-op event publisher) so each
 * test runs inside {@code @DataJpaTest}'s rolled-back transaction — fast and
 * deterministic. Optimistic-locking under true concurrency is covered separately
 * by {@code OrderEngineConcurrencyTest}.
 *
 * <p>Each test runs in a rolled-back transaction ({@code @Transactional}); the
 * engine is built by hand with a no-op event publisher and is therefore <em>not</em>
 * proxied, so it executes inside that transaction (rather than starting its own)
 * and an expected rejection doesn't mark it rollback-only — leaving the assertions
 * that follow free to read state back.
 */
@SpringBootTest
@Transactional
class OrderEngineTest extends PostgresTestcontainer {

    private static final ApplicationEventPublisher NO_EVENTS = event -> {};

    @Autowired
    private UserRepository users;

    @Autowired
    private FighterRepository fighters;

    @Autowired
    private HoldingRepository holdings;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private TradeRepository trades;

    private OrderService engine;
    private Fighter fighter;
    private int seq;

    @BeforeEach
    void setUp() {
        // No @Transactional proxy here, so placeOrder runs in the test's own
        // (rolled-back) transaction — exactly what we want for deterministic tests.
        engine = new OrderService(
            fighters,
            users,
            holdings,
            orders,
            trades,
            NO_EVENTS,
            new SimpleMeterRegistry()
        );
        fighter = newFighter(1_000);
    }

    // ------------------------------------------------------------------
    // Conservation invariants
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
        "a limit buy crossing a resting ask conserves shares and money"
    )
    void limitBuyAgainstRestingAsk_conserves() {
        User seller = newUser(0);
        giveShares(seller, 100, 1_000);
        User buyer = newUser(1_000_000);

        long coinsBefore = totalCoins(seller, buyer);
        long sharesBefore = totalShares(seller, buyer);

        place(seller, sellLimit(1_000, 10));
        OrderDto buy = place(buyer, buyLimit(1_000, 10));

        assertThat(buy.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(buy.filledQuantity()).isEqualTo(10);
        assertThat(sharesOf(buyer)).isEqualTo(10);
        assertThat(sharesOf(seller)).isEqualTo(90);
        // The crux: nothing created or destroyed, only moved.
        assertThat(totalShares(seller, buyer)).isEqualTo(sharesBefore);
        assertThat(totalCoins(seller, buyer)).isEqualTo(coinsBefore);
    }

    @Test
    @DisplayName(
        "a limit buy above the ask pays the ask price and refunds the overage"
    )
    void limitBuyAboveAsk_refundsOverage_conservesMoney() {
        User seller = newUser(0);
        giveShares(seller, 10, 1_000);
        User buyer = newUser(1_000_000);
        long coinsBefore = totalCoins(seller, buyer);

        place(seller, sellLimit(1_000, 5));
        // Reserves 5 * 1200, but fills at the resting ask of 1000.
        OrderDto buy = place(buyer, buyLimit(1_200, 5));

        assertThat(buy.status()).isEqualTo(OrderStatus.FILLED);
        // Paid 5 * 1000; the 5 * 200 overage was handed back — not kept reserved.
        assertThat(coinsAvailable(buyer)).isEqualTo(1_000_000 - 5_000);
        assertThat(coinsReserved(buyer)).isZero();
        assertThat(avgPrice(buyer)).isEqualTo(1_000);
        assertThat(totalCoins(seller, buyer)).isEqualTo(coinsBefore);
    }

    // ------------------------------------------------------------------
    // Price / time priority
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a buy fills the cheapest ask first (price priority)")
    void buy_fillsCheapestAskFirst() {
        User cheap = newUser(0);
        giveShares(cheap, 5, 800);
        User dear = newUser(0);
        giveShares(dear, 5, 800);
        User buyer = newUser(1_000_000);

        place(dear, sellLimit(1_100, 5)); // posted first, but pricier
        place(cheap, sellLimit(900, 5));
        place(buyer, buyLimit(1_100, 5)); // willing to pay up to 1100

        assertThat(sharesOf(cheap)).isZero(); // cheaper ask consumed
        assertThat(sharesOf(dear)).isEqualTo(5); // pricier ask untouched
        assertThat(avgPrice(buyer)).isEqualTo(900); // traded at the best price
        assertThat(
            fighters.findById(fighter.getId()).orElseThrow().getLastPrice()
        ).isEqualTo(900);
    }

    @Test
    @DisplayName(
        "at the same price, the oldest resting ask fills first (time priority)"
    )
    void buy_fillsOldestAskFirstAtSamePrice() throws InterruptedException {
        User first = newUser(0);
        giveShares(first, 5, 800);
        User second = newUser(0);
        giveShares(second, 5, 800);
        User buyer = newUser(1_000_000);

        place(first, sellLimit(1_000, 5));
        Thread.sleep(5); // guarantee a strictly later created_at
        place(second, sellLimit(1_000, 5));
        place(buyer, buyLimit(1_000, 5)); // only enough demand for one

        assertThat(sharesOf(first)).isZero(); // the earlier order filled
        assertThat(sharesOf(second)).isEqualTo(5);
    }

    // ------------------------------------------------------------------
    // Partial fills
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
        "a limit buy larger than the book fills what it can and rests the remainder"
    )
    void limitBuy_partiallyFills_remainderRests() {
        User seller = newUser(0);
        giveShares(seller, 3, 1_000);
        User buyer = newUser(1_000_000);

        place(seller, sellLimit(1_000, 3));
        OrderDto buy = place(buyer, buyLimit(1_000, 10));

        assertThat(buy.status()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(buy.filledQuantity()).isEqualTo(3);
        assertThat(filledFromTrades(buyer)).isEqualTo(3); // fills sum to filledQuantity
        assertThat(sharesOf(buyer)).isEqualTo(3);
        // The unfilled 7 stays reserved at the limit price (7 * 1000).
        assertThat(coinsReserved(buyer)).isEqualTo(7_000);
        assertThat(coinsAvailable(buyer)).isEqualTo(1_000_000 - 10_000);
    }

    @Test
    @DisplayName(
        "a market buy larger than the book fills what it can and cancels the rest"
    )
    void marketBuy_partiallyFills_cancelsRemainder_releasesReservation() {
        User seller = newUser(0);
        giveShares(seller, 3, 1_000);
        User buyer = newUser(1_000_000);

        place(seller, sellLimit(1_000, 3));
        OrderDto buy = place(buyer, buyMarket(10));

        assertThat(buy.status()).isEqualTo(OrderStatus.CANCELLED); // market never rests
        assertThat(buy.filledQuantity()).isEqualTo(3);
        assertThat(sharesOf(buyer)).isEqualTo(3);
        // Only the 3 actually bought leave the wallet; nothing stays reserved.
        assertThat(coinsAvailable(buyer)).isEqualTo(1_000_000 - 3_000);
        assertThat(coinsReserved(buyer)).isZero();
    }

    // ------------------------------------------------------------------
    // Self-trade prevention
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a user never trades against their own resting order")
    void selfTrade_isPrevented() {
        User user = newUser(1_000_000);
        giveShares(user, 5, 1_000);

        place(user, sellLimit(1_000, 5)); // own ask
        OrderDto buy = place(user, buyLimit(1_000, 5)); // would cross — but it's theirs

        assertThat(buy.status()).isEqualTo(OrderStatus.OPEN); // rested, did not match
        assertThat(buy.filledQuantity()).isZero();
        assertThat(tradesForFighter()).isZero();
        assertThat(sharesOf(user)).isEqualTo(5); // unchanged
        // Money only moved available -> reserved (ask reserved shares, bid reserved coins).
        assertThat(coinsAvailable(user) + coinsReserved(user)).isEqualTo(
            1_000_000
        );
    }

    // ------------------------------------------------------------------
    // Reservation release on cancel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancelling a resting buy releases exactly the reserved coins")
    void cancelBuy_releasesReservedCoins() {
        User buyer = newUser(1_000_000);
        OrderDto buy = place(buyer, buyLimit(1_000, 5)); // no asks -> rests

        assertThat(coinsReserved(buyer)).isEqualTo(5_000);
        assertThat(coinsAvailable(buyer)).isEqualTo(1_000_000 - 5_000);

        OrderDto cancelled = engine.cancelOrder(buyer, buy.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(coinsReserved(buyer)).isZero();
        assertThat(coinsAvailable(buyer)).isEqualTo(1_000_000); // fully restored
    }

    @Test
    @DisplayName(
        "cancelling a resting sell releases exactly the reserved shares"
    )
    void cancelSell_releasesReservedShares() {
        User seller = newUser(0);
        giveShares(seller, 10, 1_000);
        OrderDto sell = place(seller, sellLimit(1_000, 4)); // no bids -> rests

        assertThat(reservedShares(seller)).isEqualTo(4);

        engine.cancelOrder(seller, sell.id());

        assertThat(reservedShares(seller)).isZero();
        assertThat(sharesOf(seller)).isEqualTo(10); // shares never left
    }

    // ------------------------------------------------------------------
    // Rejections
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a limit buy beyond the wallet is rejected")
    void limitBuy_insufficientFunds_rejected() {
        User buyer = newUser(100);
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> place(buyer, buyLimit(1_000, 5))
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("insufficient funds");
    }

    @Test
    @DisplayName("selling more shares than owned is rejected")
    void sell_insufficientShares_rejected() {
        User seller = newUser(0);
        giveShares(seller, 2, 1_000);
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> place(seller, sellLimit(1_000, 5))
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("insufficient shares");
    }

    @Test
    @DisplayName(
        "a market order with no liquidity is rejected (and records no trade)"
    )
    void marketBuy_noLiquidity_rejected() {
        User buyer = newUser(1_000_000);
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> place(buyer, buyMarket(5))
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tradesForFighter()).isZero();
    }

    @Test
    @DisplayName("a limit buy below the best ask does not cross — it rests")
    void limitBuy_belowAsk_doesNotCross() {
        User seller = newUser(0);
        giveShares(seller, 5, 1_000);
        place(seller, sellLimit(1_000, 5));

        User buyer = newUser(1_000_000);
        OrderDto buy = place(buyer, buyLimit(900, 5)); // below the ask

        assertThat(buy.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(buy.filledQuantity()).isZero();
        assertThat(tradesForFighter()).isZero();
        assertThat(coinsReserved(buyer)).isEqualTo(4_500); // 5 * 900 held
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private OrderDto place(User user, PlaceOrderDTO dto) {
        return engine.placeOrder(user, dto);
    }

    private PlaceOrderDTO buyLimit(long price, int qty) {
        return new PlaceOrderDTO(
            id(),
            OrderSide.BUY,
            OrderType.LIMIT,
            price,
            qty
        );
    }

    private PlaceOrderDTO sellLimit(long price, int qty) {
        return new PlaceOrderDTO(
            id(),
            OrderSide.SELL,
            OrderType.LIMIT,
            price,
            qty
        );
    }

    private PlaceOrderDTO buyMarket(int qty) {
        return new PlaceOrderDTO(
            id(),
            OrderSide.BUY,
            OrderType.MARKET,
            null,
            qty
        );
    }

    private String id() {
        return fighter.getId().toString();
    }

    private Fighter newFighter(long lastPrice) {
        Fighter f = new Fighter();
        f.setName("fighter-" + (++seq) + "-" + System.nanoTime());
        f.setStatus(Status.ACTIVE);
        f.setLastPrice(lastPrice);
        return fighters.save(f);
    }

    private User newUser(long coins) {
        User u = new User();
        u.setUsername("u-" + (++seq) + "-" + System.nanoTime());
        u.setPasswordHash("x");
        u.setAvailableCoins(coins);
        u.setReservedCoins(0);
        return users.save(u);
    }

    private void giveShares(User user, long qty, long avgPrice) {
        Holding h = new Holding();
        h.setUser(user);
        h.setFighter(fighter);
        h.setQuantity(qty);
        h.setReservedQuantity(0);
        h.setAveragePrice(avgPrice);
        holdings.save(h);
    }

    private long sharesOf(User user) {
        return holdings
            .findByUserAndFighter(user, fighter)
            .map(Holding::getQuantity)
            .orElse(0L);
    }

    private long reservedShares(User user) {
        return holdings
            .findByUserAndFighter(user, fighter)
            .map(Holding::getReservedQuantity)
            .orElse(0L);
    }

    private long avgPrice(User user) {
        return holdings
            .findByUserAndFighter(user, fighter)
            .map(Holding::getAveragePrice)
            .orElse(0L);
    }

    private long coinsAvailable(User user) {
        return users.findById(user.getId()).orElseThrow().getAvailableCoins();
    }

    private long coinsReserved(User user) {
        return users.findById(user.getId()).orElseThrow().getReservedCoins();
    }

    private long totalCoins(User... us) {
        long total = 0;
        for (User u : us) {
            User fresh = users.findById(u.getId()).orElseThrow();
            total += fresh.getAvailableCoins() + fresh.getReservedCoins();
        }
        return total;
    }

    private long totalShares(User... us) {
        long total = 0;
        for (User u : us) {
            total += sharesOf(u);
        }
        return total;
    }

    /** Trades recorded for this test's fighter (scoped so other tests' commits don't leak in). */
    private long tradesForFighter() {
        return trades
            .findAll()
            .stream()
            .filter(t -> t.getFighter().getId().equals(fighter.getId()))
            .count();
    }

    /** Sum of the user's executed fill quantities — must equal filledQuantity. */
    private long filledFromTrades(User user) {
        return trades
            .findAll()
            .stream()
            .filter(t -> t.getBuyOrder().getUser().getId().equals(user.getId()))
            .mapToLong(Trade::getQuantity)
            .sum();
    }
}
