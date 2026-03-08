package com.eolma.auction.application.usecase;

import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.application.port.out.EventPublisher;
import com.eolma.auction.domain.model.Auction;
import com.eolma.auction.domain.service.AuctionService;
import com.eolma.common.event.DomainEvent;
import com.eolma.common.event.EventType;
import com.eolma.common.event.payload.AuctionStartedEvent;
import com.eolma.common.event.payload.ProductActivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class CreateAuctionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAuctionUseCase.class);
    private final AuctionService auctionService;
    private final AuctionCachePort auctionCachePort;
    private final EventPublisher eventPublisher;

    public CreateAuctionUseCase(AuctionService auctionService,
                                 AuctionCachePort auctionCachePort,
                                 EventPublisher eventPublisher) {
        this.auctionService = auctionService;
        this.auctionCachePort = auctionCachePort;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Auction> execute(ProductActivatedEvent event) {
        log.info("Creating auction from product: productId={}", event.productId());

        return auctionService.createAuction(
                        event.productId(),
                        event.sellerId(),
                        event.title(),
                        event.startingPrice(),
                        event.instantPrice(),
                        event.instantBuyLockPercent(),
                        event.reservePrice(),
                        event.minBidUnit(),
                        event.endType(),
                        event.durationHours(),
                        event.maxBidCount()
                )
                .flatMap(auction ->
                        auctionCachePort.initAuctionCache(auction.getId(), auction.getSellerId(),
                                        auction.getCurrentPrice(), auction.getMinBidUnit(),
                                        auction.getInstantPrice(), auction.getInstantBuyLockPercent(),
                                        auction.getEndAt())
                                .then(auctionCachePort.scheduleEnding(auction.getId(), auction.getEndAt()))
                                .then(Mono.fromRunnable(() -> publishAuctionStartedEvent(auction)))
                                .thenReturn(auction)
                );
    }

    private void publishAuctionStartedEvent(Auction auction) {
        AuctionStartedEvent payload = new AuctionStartedEvent(
                auction.getId(),
                auction.getProductId(),
                auction.getStartingPrice(),
                auction.getInstantPrice(),
                auction.getReservePrice(),
                auction.getMinBidUnit(),
                auction.getEndType(),
                auction.getDurationHours(),
                auction.getMaxBidCount(),
                LocalDateTime.now()
        );

        DomainEvent<AuctionStartedEvent> domainEvent = DomainEvent.create(
                EventType.AUCTION_STARTED,
                "eolma-auction",
                String.valueOf(auction.getId()),
                "Auction",
                payload
        );

        eventPublisher.publish(domainEvent);
    }
}
