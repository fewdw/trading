package com.ufc.server.ranking;

import com.ufc.server.dto.FighterDto;
import com.ufc.server.dto.OrderbookDto;
import com.ufc.server.dto.TradeDto;
import com.ufc.server.order.Order;
import com.ufc.server.order.OrderRepository;
import com.ufc.server.trade.TradeRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FighterService {

    private static final int DEFAULT_TRADE_LIMIT = 50;

    private final FighterRepository fighterRepository;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    public FighterService(
        FighterRepository fighterRepository,
        OrderRepository orderRepository,
        TradeRepository tradeRepository
    ) {
        this.fighterRepository = fighterRepository;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
    }

    public List<FighterDto> getAllFighters(String status) {
        List<Fighter> fighters;

        if (status == null || status.isBlank()) {
            fighters = fighterRepository.findAll();
        } else {
            Status parsed;
            try {
                parsed = Status.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown status: " + status
                );
            }
            fighters = fighterRepository.findAllByStatus(parsed);
        }

        return fighters.stream().map(this::toDto).toList();
    }

    public FighterDto getFighterById(Long fighterId) {
        Fighter fighter = fighterRepository
            .findById(fighterId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Fighter not found: " + fighterId
                )
            );
        return toDto(fighter);
    }

    public OrderbookDto getFighterOrderbook(Long fighterId) {
        Fighter fighter = fighterRepository
            .findById(fighterId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Fighter not found: " + fighterId
                )
            );

        List<Order> bids = orderRepository.findOpenBids(fighter);
        List<Order> asks = orderRepository.findOpenAsks(fighter);

        return new OrderbookDto(
            aggregate(bids, Comparator.reverseOrder()),
            aggregate(asks, Comparator.naturalOrder())
        );
    }

    public List<TradeDto> getFighterTrades(Long fighterId) {
        Fighter fighter = requireFighter(fighterId);

        return tradeRepository
            .findByFighterOrderByExecutedAtDesc(
                fighter,
                PageRequest.of(0, DEFAULT_TRADE_LIMIT)
            )
            .stream()
            .map(t ->
                new TradeDto(t.getPrice(), t.getQuantity(), t.getExecutedAt())
            )
            .toList();
    }

    private Fighter requireFighter(Long fighterId) {
        return fighterRepository
            .findById(fighterId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Fighter not found: " + fighterId
                )
            );
    }

    private List<OrderbookDto.PriceLevel> aggregate(
        List<Order> orders,
        Comparator<Long> priceOrder
    ) {
        return orders
            .stream()
            .filter(o -> o.getLimitPrice() != null)
            .collect(
                Collectors.groupingBy(
                    Order::getLimitPrice,
                    Collectors.summingLong(
                        o -> o.getQuantity() - o.getFilledQuantity()
                    )
                )
            )
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 0)
            .sorted(Map.Entry.comparingByKey(priceOrder))
            .map(e -> new OrderbookDto.PriceLevel(e.getKey(), e.getValue()))
            .toList();
    }

    private FighterDto toDto(Fighter fighter) {
        return new FighterDto(
            fighter.getId(),
            fighter.getName(),
            fighter.getPhoto(),
            fighter.getStatus(),
            fighter.getLastPrice()
        );
    }
}
