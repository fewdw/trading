package com.ufc.server.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.ufc.server.dto.PlaceOrderDTO;
import com.ufc.server.holding.Holding;
import com.ufc.server.holding.HoldingRepository;
import com.ufc.server.ranking.Fighter;
import com.ufc.server.ranking.FighterRepository;
import com.ufc.server.ranking.Status;
import com.ufc.server.support.PostgresTestcontainer;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Concurrency test for the matching engine against a real Postgres
 * (Testcontainers). Several buyers race to take the <em>same</em> single resting
 * ask — far more demand than supply. The engine's correctness rests on JPA
 * optimistic locking ({@code @Version} on {@code User}/{@code Holding}): the first
 * fill to commit wins, and any competing fill's transaction is rolled back whole.
 *
 * <p>Unlike {@code OrderEngineTest}, this uses the real Spring-managed
 * {@link OrderService} bean so each {@code placeOrder} runs in its own committed
 * transaction — the only way to exercise the lock-conflict path. The assertions
 * are the system invariants, which must hold no matter how the threads interleave:
 * shares are conserved, coins are neither minted nor burned, and the ask is never
 * oversold.
 */
@SpringBootTest
class OrderEngineConcurrencyTest extends PostgresTestcontainer {

    private static final long ASK_PRICE = 1_000;
    private static final int ASK_SIZE = 10;
    private static final int BUYERS = 6;
    private static final long BUYER_COINS = 1_000_000;

    @Autowired
    private OrderService engine;

    @Autowired
    private UserRepository users;

    @Autowired
    private FighterRepository fighters;

    @Autowired
    private HoldingRepository holdings;

    @Test
    void concurrentBuysAgainstOneAsk_neverOversellOrMintCoins()
        throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Fighter fighter = saveFighter(tag);
        User seller = saveUser("seller-" + tag, 0);
        giveShares(seller, fighter, ASK_SIZE);

        // A single resting ask for the whole inventory.
        engine.placeOrder(
            seller,
            new PlaceOrderDTO(
                fighter.getId().toString(),
                OrderSide.SELL,
                OrderType.LIMIT,
                ASK_PRICE,
                ASK_SIZE
            )
        );

        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < BUYERS; i++) {
            buyers.add(saveUser("buyer-" + tag + "-" + i, BUYER_COINS));
        }

        List<User> everyone = new ArrayList<>(buyers);
        everyone.add(seller);
        long coinsBefore = totalCoins(everyone);
        long sharesBefore = totalShares(fighter, everyone);

        // Fire every buyer at the same instant to provoke lock contention.
        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        CyclicBarrier barrier = new CyclicBarrier(BUYERS);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Future<?>> tasks = new ArrayList<>();
        for (User buyer : buyers) {
            tasks.add(
                pool.submit(() -> {
                    try {
                        barrier.await();
                        engine.placeOrder(
                            buyer,
                            new PlaceOrderDTO(
                                fighter.getId().toString(),
                                OrderSide.BUY,
                                OrderType.LIMIT,
                                ASK_PRICE,
                                ASK_SIZE
                            )
                        );
                    } catch (Throwable t) {
                        // Lock conflicts (and "no liquidity left" rejections) are
                        // expected for the losers — the invariants below are what
                        // must hold, not any single attempt's outcome.
                        failures.add(t);
                    }
                })
            );
        }
        for (Future<?> task : tasks) {
            task.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        long bought = buyers
            .stream()
            .mapToLong(b -> sharesHeld(b, fighter))
            .sum();
        long winners = buyers
            .stream()
            .filter(b -> sharesHeld(b, fighter) > 0)
            .count();

        // Shares conserved: trading only moved them around.
        assertThat(totalShares(fighter, everyone)).isEqualTo(sharesBefore);
        // Money conserved: no coins minted or burned despite the contention.
        assertThat(totalCoins(everyone)).isEqualTo(coinsBefore);
        // The ask was filled exactly once, never oversold.
        assertThat(bought).isEqualTo(ASK_SIZE);
        assertThat(winners).isEqualTo(1);
        assertThat(sharesHeld(seller, fighter)).isZero();
    }

    // ------------------------------------------------------------------
    // helpers (each save commits — there is no surrounding test transaction)
    // ------------------------------------------------------------------

    private Fighter saveFighter(String tag) {
        Fighter f = new Fighter();
        f.setName("fighter-" + tag);
        f.setStatus(Status.ACTIVE);
        f.setLastPrice(ASK_PRICE);
        return fighters.save(f);
    }

    private User saveUser(String username, long coins) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash("x");
        u.setAvailableCoins(coins);
        u.setReservedCoins(0);
        return users.save(u);
    }

    private void giveShares(User user, Fighter fighter, long qty) {
        Holding h = new Holding();
        h.setUser(user);
        h.setFighter(fighter);
        h.setQuantity(qty);
        h.setReservedQuantity(0);
        h.setAveragePrice(ASK_PRICE);
        holdings.save(h);
    }

    private long sharesHeld(User user, Fighter fighter) {
        return holdings
            .findByUserAndFighter(user, fighter)
            .map(Holding::getQuantity)
            .orElse(0L);
    }

    private long totalShares(Fighter fighter, List<User> us) {
        return us
            .stream()
            .mapToLong(u -> sharesHeld(u, fighter))
            .sum();
    }

    private long totalCoins(List<User> us) {
        long total = 0;
        for (User u : us) {
            User fresh = users.findById(u.getId()).orElseThrow();
            total += fresh.getAvailableCoins() + fresh.getReservedCoins();
        }
        return total;
    }
}
