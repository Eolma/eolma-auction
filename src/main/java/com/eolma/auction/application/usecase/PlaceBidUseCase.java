package com.eolma.auction.application.usecase;

import com.eolma.auction.adapter.in.websocket.WebSocketSessionManager;
import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.application.port.out.DistributedLockPort;
import com.eolma.auction.application.port.out.EventPublisher;
import com.eolma.auction.domain.model.Bid;
import com.eolma.auction.domain.model.BidResult;
import com.eolma.auction.domain.model.BidType;
import com.eolma.auction.domain.repository.BidRepository;
import com.eolma.auction.domain.service.AuctionService;
import com.eolma.auction.domain.service.BidValidationService;
import com.eolma.common.event.DomainEvent;
import com.eolma.common.event.EventType;
import com.eolma.common.event.payload.BidPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class PlaceBidUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlaceBidUseCase.class);
    private final BidValidationService bidValidationService;
    private final AuctionService auctionService;
    private final BidRepository bidRepository;
    private final AuctionCachePort auctionCachePort;
    private final DistributedLockPort distributedLockPort;
    private final EventPublisher eventPublisher;
    private final WebSocketSessionManager sessionManager;
    private final CloseAuctionUseCase closeAuctionUseCase;

    public PlaceBidUseCase(BidValidationService bidValidationService,
                            AuctionService auctionService,
                            BidRepository bidRepository,
                            AuctionCachePort auctionCachePort,
                            DistributedLockPort distributedLockPort,
                            EventPublisher eventPublisher,
                            WebSocketSessionManager sessionManager,
                            CloseAuctionUseCase closeAuctionUseCase) {
        this.bidValidationService = bidValidationService;
        this.auctionService = auctionService;
        this.bidRepository = bidRepository;
        this.auctionCachePort = auctionCachePort;
        this.distributedLockPort = distributedLockPort;
        this.eventPublisher = eventPublisher;
        this.sessionManager = sessionManager;
        this.closeAuctionUseCase = closeAuctionUseCase;
    }

    public Mono<BidResult> execute(Long auctionId, Long bidderId, Long amount) {
        log.info("Processing bid: auctionId={}, bidderId={}, amount={}", auctionId, bidderId, amount);

        return Mono.fromCallable(() ->
                distributedLockPort.executeWithLock("lock:auction:" + auctionId, () ->
                        processBidWithLock(auctionId, bidderId, amount).block()
                )
        ).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<BidResult> processBidWithLock(Long auctionId, Long bidderId, Long amount) {
        return auctionCachePort.getAuctionState(auctionId)
                .flatMap(state -> {
                    if (state.isEmpty()) {
                        return auctionService.findById(auctionId)
                                .map(auction -> Map.of(
                                        "currentPrice", String.valueOf(auction.getCurrentPrice()),
                                        "winnerId", auction.getWinnerId() != null ? String.valueOf(auction.getWinnerId()) : "0",
                                        "bidCount", String.valueOf(auction.getBidCount()),
                                        "sellerId", String.valueOf(auction.getSellerId()),
                                        "minBidUnit", String.valueOf(auction.getMinBidUnit()),
                                        "status", auction.getStatus(),
                                        "instantPrice", auction.getInstantPrice() != null ? String.valueOf(auction.getInstantPrice()) : "0",
                                        "endAt", auction.getEndAt().toString()
                                ));
                    }
                    return Mono.just(state);
                })
                .flatMap(state -> validateAndPlaceBid(auctionId, bidderId, amount, state));
    }

    private Mono<BidResult> validateAndPlaceBid(Long auctionId, Long bidderId, Long amount, Map<String, String> state) {
        Long currentPrice = Long.parseLong(state.getOrDefault("currentPrice", "0"));
        Long winnerId = Long.parseLong(state.getOrDefault("winnerId", "0"));
        Long sellerId = Long.parseLong(state.getOrDefault("sellerId", "0"));
        Long minBidUnit = Long.parseLong(state.getOrDefault("minBidUnit", "1000"));
        String status = state.getOrDefault("status", "ACTIVE");
        Long instantPrice = Long.parseLong(state.getOrDefault("instantPrice", "0"));
        int bidCount = Integer.parseInt(state.getOrDefault("bidCount", "0"));

        BidResult validationResult = bidValidationService.validate(
                bidderId, sellerId, amount, currentPrice, minBidUnit, status, winnerId == 0 ? null : winnerId);

        if (validationResult != null) {
            return Mono.just(validationResult);
        }

        boolean isInstantBuy = instantPrice > 0 && amount >= instantPrice;
        BidType bidType = isInstantBuy ? BidType.INSTANT : BidType.MANUAL;
        int newBidCount = bidCount + 1;

        Bid bid = Bid.create(auctionId, bidderId, amount, bidType);

        return bidRepository.save(bid)
                .flatMap(savedBid ->
                        auctionCachePort.updateBidState(auctionId, amount, bidderId, newBidCount)
                                .then(auctionCachePort.addBidToRanking(auctionId, bidderId, amount))
                                .then(auctionService.updateBidInfo(auctionId, amount, newBidCount))
                                .then(Mono.fromRunnable(() -> {
                                    publishBidPlacedEvent(savedBid, auctionId, currentPrice, newBidCount);
                                    String endAtStr = state.get("endAt");
                                    LocalDateTime endAt = endAtStr != null
                                            ? LocalDateTime.parse(endAtStr)
                                            : LocalDateTime.now();
                                    sessionManager.broadcastAuctionUpdate(auctionId, amount, newBidCount, endAt);
                                }))
                                .then(isInstantBuy
                                        ? closeAuctionUseCase.execute(auctionId).then()
                                        : Mono.empty())
                                .thenReturn(BidResult.success(savedBid.getId(), amount, newBidCount, minBidUnit))
                );
    }

    private void publishBidPlacedEvent(Bid bid, Long auctionId, Long previousPrice, int bidSequence) {
        BidPlacedEvent payload = new BidPlacedEvent(
                bid.getId(), auctionId, bid.getBidderId(),
                bid.getAmount(), previousPrice, bidSequence, LocalDateTime.now()
        );

        DomainEvent<BidPlacedEvent> event = DomainEvent.create(
                EventType.BID_PLACED, "eolma-auction",
                String.valueOf(auctionId), "Auction", payload
        );

        eventPublisher.publish(event);
    }
}
