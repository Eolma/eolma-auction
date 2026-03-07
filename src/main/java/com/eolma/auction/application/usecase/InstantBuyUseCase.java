package com.eolma.auction.application.usecase;

import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.application.port.out.DistributedLockPort;
import com.eolma.auction.application.port.out.EventPublisher;
import com.eolma.auction.domain.model.BidResult;
import com.eolma.auction.domain.service.AuctionService;
import com.eolma.auction.domain.service.BidValidationService;
import com.eolma.common.event.DomainEvent;
import com.eolma.common.event.EventType;
import com.eolma.common.event.payload.InstantBuyStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class InstantBuyUseCase {

    private static final Logger log = LoggerFactory.getLogger(InstantBuyUseCase.class);
    private static final int RESERVATION_MINUTES = 5;

    private final AuctionService auctionService;
    private final AuctionCachePort auctionCachePort;
    private final DistributedLockPort distributedLockPort;
    private final EventPublisher eventPublisher;
    private final BidValidationService bidValidationService;

    public InstantBuyUseCase(AuctionService auctionService,
                              AuctionCachePort auctionCachePort,
                              DistributedLockPort distributedLockPort,
                              EventPublisher eventPublisher,
                              BidValidationService bidValidationService) {
        this.auctionService = auctionService;
        this.auctionCachePort = auctionCachePort;
        this.distributedLockPort = distributedLockPort;
        this.eventPublisher = eventPublisher;
        this.bidValidationService = bidValidationService;
    }

    public Mono<InstantBuyResult> execute(Long auctionId, Long buyerId) {
        log.info("Processing instant buy reservation: auctionId={}, buyerId={}", auctionId, buyerId);

        return Mono.fromCallable(() ->
                distributedLockPort.executeWithLock("lock:auction:" + auctionId, () ->
                        processInstantBuy(auctionId, buyerId).block()
                )
        ).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<InstantBuyResult> processInstantBuy(Long auctionId, Long buyerId) {
        return auctionCachePort.getAuctionState(auctionId)
                .flatMap(state -> {
                    if (state.isEmpty()) {
                        return auctionService.findById(auctionId)
                                .map(auction -> Map.of(
                                        "status", auction.getStatus(),
                                        "sellerId", String.valueOf(auction.getSellerId()),
                                        "instantPrice", auction.getInstantPrice() != null ? String.valueOf(auction.getInstantPrice()) : "0"
                                ));
                    }
                    return Mono.just(state);
                })
                .flatMap(state -> validateAndReserve(auctionId, buyerId, state));
    }

    private Mono<InstantBuyResult> validateAndReserve(Long auctionId, Long buyerId, Map<String, String> state) {
        String status = state.getOrDefault("status", "ACTIVE");
        Long sellerId = Long.parseLong(state.getOrDefault("sellerId", "0"));
        Long instantPrice = Long.parseLong(state.getOrDefault("instantPrice", "0"));

        if (instantPrice <= 0) {
            return Mono.just(InstantBuyResult.failure("NO_INSTANT_PRICE", "즉시구매가 설정되지 않은 경매입니다."));
        }

        // BidValidationService의 상태/판매자/최고입찰자 검증 재활용
        Long currentPrice = Long.parseLong(state.getOrDefault("currentPrice", "0"));
        Long minBidUnit = Long.parseLong(state.getOrDefault("minBidUnit", "1000"));
        Long winnerId = Long.parseLong(state.getOrDefault("winnerId", "0"));
        BidResult validation = bidValidationService.validate(
                buyerId, sellerId, instantPrice, currentPrice, minBidUnit, status,
                winnerId == 0 ? null : winnerId);
        if (validation != null) {
            return Mono.just(InstantBuyResult.failure(validation.errorCode(), validation.errorMessage()));
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RESERVATION_MINUTES);

        return auctionCachePort.setInstantBuyReservation(auctionId, buyerId)
                .then(auctionCachePort.patchField(auctionId, "status", "PENDING_INSTANT_BUY"))
                .then(auctionService.setPendingInstantBuy(auctionId))
                .flatMap(auction -> {
                    publishInstantBuyStartedEvent(auction.getId(), auction.getProductId(),
                            auction.getSellerId(), buyerId, instantPrice, expiresAt);
                    return Mono.just(InstantBuyResult.success(auctionId, instantPrice, expiresAt));
                });
    }

    private void publishInstantBuyStartedEvent(Long auctionId, Long productId, Long sellerId,
                                                Long buyerId, Long price, LocalDateTime expiresAt) {
        InstantBuyStartedEvent payload = new InstantBuyStartedEvent(
                auctionId, productId, sellerId, buyerId, price, expiresAt);

        eventPublisher.publish(DomainEvent.create(
                EventType.INSTANT_BUY_STARTED, "eolma-auction",
                String.valueOf(auctionId), "Auction", payload));
    }

    public record InstantBuyResult(
            boolean accepted,
            Long auctionId,
            Long price,
            LocalDateTime expiresAt,
            String errorCode,
            String errorMessage
    ) {
        public static InstantBuyResult success(Long auctionId, Long price, LocalDateTime expiresAt) {
            return new InstantBuyResult(true, auctionId, price, expiresAt, null, null);
        }

        public static InstantBuyResult failure(String errorCode, String errorMessage) {
            return new InstantBuyResult(false, null, null, null, errorCode, errorMessage);
        }
    }
}
