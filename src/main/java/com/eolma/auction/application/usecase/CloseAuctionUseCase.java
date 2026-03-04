package com.eolma.auction.application.usecase;

import com.eolma.auction.adapter.in.websocket.WebSocketSessionManager;
import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.application.port.out.DistributedLockPort;
import com.eolma.auction.application.port.out.EventPublisher;
import com.eolma.auction.domain.model.Auction;
import com.eolma.auction.domain.service.AuctionService;
import com.eolma.common.event.DomainEvent;
import com.eolma.common.event.EventType;
import com.eolma.common.event.payload.AuctionCompletedEvent;
import com.eolma.common.event.payload.AuctionFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class CloseAuctionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CloseAuctionUseCase.class);
    private final AuctionService auctionService;
    private final AuctionCachePort auctionCachePort;
    private final DistributedLockPort distributedLockPort;
    private final EventPublisher eventPublisher;
    private final WebSocketSessionManager sessionManager;

    public CloseAuctionUseCase(AuctionService auctionService,
                                AuctionCachePort auctionCachePort,
                                DistributedLockPort distributedLockPort,
                                EventPublisher eventPublisher,
                                WebSocketSessionManager sessionManager) {
        this.auctionService = auctionService;
        this.auctionCachePort = auctionCachePort;
        this.distributedLockPort = distributedLockPort;
        this.eventPublisher = eventPublisher;
        this.sessionManager = sessionManager;
    }

    public Mono<Void> execute(Long auctionId) {
        log.info("Closing auction: auctionId={}", auctionId);

        return Mono.fromCallable(() -> {
            distributedLockPort.executeWithLock("lock:auction:" + auctionId, () -> {
                closeAuctionWithLock(auctionId).block();
                return null;
            });
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Void> closeAuctionWithLock(Long auctionId) {
        return auctionCachePort.getAuctionState(auctionId)
                .flatMap(state -> auctionService.findById(auctionId)
                        .flatMap(auction -> {
                            if (!auction.isActive()) {
                                log.debug("Auction already closed: auctionId={}", auctionId);
                                return Mono.empty();
                            }

                            Long winnerId = parseOrNull(state.get("winnerId"));
                            Long currentPrice = parseLong(state.getOrDefault("currentPrice", "0"));
                            int bidCount = Integer.parseInt(state.getOrDefault("bidCount", "0"));

                            boolean hasValidWinner = winnerId != null && winnerId > 0;
                            boolean meetsReservePrice = !auction.hasReservePrice()
                                    || currentPrice >= auction.getReservePrice();

                            if (hasValidWinner && meetsReservePrice) {
                                return completeAuction(auction, winnerId, currentPrice, bidCount);
                            } else {
                                return failAuction(auction);
                            }
                        })
                );
    }

    private Mono<Void> completeAuction(Auction auction, Long winnerId, Long winningPrice, int bidCount) {
        return auctionService.completeAuction(auction.getId(), winnerId, winningPrice)
                .flatMap(completed -> {
                    publishCompletedEvent(completed, bidCount);
                    sessionManager.broadcastAuctionClosed(auction.getId(), winnerId, winningPrice, "COMPLETED");
                    return auctionCachePort.removeFromEnding(auction.getId())
                            .then(auctionCachePort.cleanupAuctionCache(auction.getId()));
                });
    }

    private Mono<Void> failAuction(Auction auction) {
        String reason = auction.hasReservePrice() ? "Reserve price not met" : "No bids received";

        return auctionService.failAuction(auction.getId())
                .flatMap(failed -> {
                    publishFailedEvent(failed, reason);
                    sessionManager.broadcastAuctionClosed(auction.getId(), null, null, "FAILED");
                    return auctionCachePort.removeFromEnding(auction.getId())
                            .then(auctionCachePort.cleanupAuctionCache(auction.getId()));
                });
    }

    private void publishCompletedEvent(Auction auction, int bidCount) {
        AuctionCompletedEvent payload = new AuctionCompletedEvent(
                auction.getId(), auction.getProductId(),
                auction.getSellerId(), auction.getWinnerId(),
                auction.getWinningPrice(), bidCount, LocalDateTime.now()
        );

        eventPublisher.publish(DomainEvent.create(
                EventType.AUCTION_COMPLETED, "eolma-auction",
                String.valueOf(auction.getId()), "Auction", payload
        ));
    }

    private void publishFailedEvent(Auction auction, String reason) {
        AuctionFailedEvent payload = new AuctionFailedEvent(
                auction.getId(), auction.getProductId(),
                reason, LocalDateTime.now()
        );

        eventPublisher.publish(DomainEvent.create(
                EventType.AUCTION_FAILED, "eolma-auction",
                String.valueOf(auction.getId()), "Auction", payload
        ));
    }

    private Long parseOrNull(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) return null;
        return Long.parseLong(value);
    }

    private Long parseLong(String value) {
        return Long.parseLong(value);
    }
}
