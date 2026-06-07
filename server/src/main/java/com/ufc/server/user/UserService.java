package com.ufc.server.user;

import com.ufc.server.holding.Holding;
import com.ufc.server.holding.HoldingRepository;
import com.ufc.server.order.Order;
import com.ufc.server.order.OrderRepository;
import com.ufc.server.order.OrderSide;
import com.ufc.server.order.OrderStatus;
import com.ufc.server.order.OrderType;
import com.ufc.server.ranking.Fighter;
import com.ufc.server.ranking.FighterRepository;
import com.ufc.server.ranking.Status;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService {

    public static final String TREASURY_USERNAME = "__TREASURY__";
    public static final long SEED_SHARES = 500;
    public static final long SEED_PRICE = 100; // 1 coin, in sub-units

    private final FighterRepository fighterRepository;
    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;

    public UserService(
        FighterRepository fighterRepository,
        UserRepository userRepository,
        HoldingRepository holdingRepository,
        OrderRepository orderRepository
    ) {
        this.fighterRepository = fighterRepository;
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Give the market initial liquidity for any active fighter the treasury
     * doesn't already back: mint {@link #SEED_SHARES} shares to the treasury and
     * post a single sell order for them. Idempotent — keyed on whether the
     * treasury already holds the fighter, so it's safe to run on every refresh.
     */
    @Transactional
    public void seedNewFightersToTreasury() {
        User treasury = userRepository
            .findByUsername(TREASURY_USERNAME)
            .orElseThrow(() ->
                new IllegalStateException("treasury account not found")
            );

        Set<Long> alreadySeeded = holdingRepository
            .findByUser(treasury)
            .stream()
            .map(h -> h.getFighter().getId())
            .collect(Collectors.toSet());

        for (Fighter fighter : fighterRepository.findAllByStatus(Status.ACTIVE)) {
            if (alreadySeeded.contains(fighter.getId())) {
                continue;
            }

            // 1. Treasury holds all the shares, fully reserved against its open
            //    sell order (none are freely available).
            Holding holding = new Holding();
            holding.setUser(treasury);
            holding.setFighter(fighter);
            holding.setQuantity(SEED_SHARES);
            holding.setReservedQuantity(SEED_SHARES);
            holding.setAveragePrice(0); // minted, no cost basis
            holdingRepository.save(holding);

            // 2. List it: a sell order for all the shares at the seed price.
            Order seedOrder = new Order();
            seedOrder.setUser(treasury);
            seedOrder.setFighter(fighter);
            seedOrder.setSide(OrderSide.SELL);
            seedOrder.setType(OrderType.LIMIT);
            seedOrder.setLimitPrice(SEED_PRICE);
            seedOrder.setQuantity(SEED_SHARES);
            seedOrder.setFilledQuantity(0);
            seedOrder.setStatus(OrderStatus.OPEN);
            orderRepository.save(seedOrder);

            log.info("seeded fighter to treasury: {}", fighter.getName());
        }
    }
}
