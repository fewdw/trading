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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    public static final String TREASURY_USERNAME = "__TREASURY__";
    public static final long IPO_SHARES = 500;
    public static final long IPO_PRICE = 100; // 1 coin, in sub-units

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

    @Transactional
    public void allocateIpoToTreasury() {
        User treasury = userRepository
            .findByUsername(TREASURY_USERNAME)
            .orElseThrow(() ->
                new IllegalStateException("treasury account not found")
            );

        List<Fighter> ipoFighters = fighterRepository.findAllByStatus(
            Status.IPO
        );

        for (Fighter fighter : ipoFighters) {
            // 1. Treasury holds all the shares, fully reserved against its
            //    open sell order (none are freely available).
            Holding holding = new Holding();
            holding.setUser(treasury);
            holding.setFighter(fighter);
            holding.setQuantity(IPO_SHARES);
            holding.setReservedQuantity(IPO_SHARES);
            holding.setAveragePrice(0); // minted, no cost basis
            holdingRepository.save(holding);

            // 2. List it: a sell order for 500 shares at 1 coin each.
            Order ipoOrder = new Order();
            ipoOrder.setUser(treasury);
            ipoOrder.setFighter(fighter);
            ipoOrder.setSide(OrderSide.SELL);
            ipoOrder.setType(OrderType.LIMIT);
            ipoOrder.setLimitPrice(IPO_PRICE);
            ipoOrder.setQuantity(IPO_SHARES);
            ipoOrder.setFilledQuantity(0);
            ipoOrder.setStatus(OrderStatus.OPEN);
            orderRepository.save(ipoOrder);
        }
        // The fighters are managed entities inside this transaction, so the
        // status change is flushed automatically on commit — no save() needed.
    }
}
