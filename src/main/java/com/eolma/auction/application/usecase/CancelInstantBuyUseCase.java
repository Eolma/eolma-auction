package com.eolma.auction.application.usecase;

import com.eolma.auction.adapter.in.websocket.WebSocketSessionManager;
import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.application.port.out.DistributedLockPort;
import com.eolma.auction.domain.service.AuctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Component
public class CancelInstantBuyUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelInstantBuyUseCase.class);
    private final AuctionService auctionService;
    private final AuctionCachePort auctionCachePort;
    private final DistributedLockPort distributedLockPort;
    private final WebSocketSessionManager sessionManager;

    public CancelInstantBuyUseCase(AuctionService auctionService,
                                    AuctionCachePort auctionCachePort,
                                    DistributedLockPort distributedLockPort,
                                    WebSocketSessionManager sessionManager) {
        this.auctionService = auctionService;
        this.auctionCachePort = auctionCachePort;
        this.distributedLockPort = distributedLockPort;
        this.sessionManager = sessionManager;
    }

    public Mono<Void> execute(Long auctionId, Long userId) {
        log.info("Cancelling instant buy reservation: auctionId={}, userId={}", auctionId, userId);

        return Mono.fromCallable(() -> {
            distributedLockPort.executeWithLock("lock:auction:" + auctionId, () -> {
                cancelReservation(auctionId, userId).block();
                return null;
            });
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 락 없이 내부 호출 (스케줄러 등에서 이미 락 보유 시)
     */
    public Mono<Void> executeWithinLock(Long auctionId) {
        return restoreAuction(auctionId);
    }

    private Mono<Void> cancelReservation(Long auctionId, Long userId) {
        return auctionCachePort.getInstantBuyReservation(auctionId)
                .flatMap(reservation -> {
                    if (reservation.isEmpty()) {
                        log.debug("No active reservation to cancel: auctionId={}", auctionId);
                        return Mono.empty();
                    }
                    String reservedBuyerId = reservation.get("buyerId");
                    if (userId != null && !String.valueOf(userId).equals(reservedBuyerId)) {
                        log.warn("Cancel rejected: userId={} is not the reservation holder", userId);
                        return Mono.empty();
                    }
                    return restoreAuction(auctionId);
                });
    }

    private Mono<Void> restoreAuction(Long auctionId) {
        return auctionCachePort.removeInstantBuyReservation(auctionId)
                .then(auctionCachePort.patchField(auctionId, "status", "ACTIVE"))
                .then(auctionService.restoreActive(auctionId))
                .then()
                .doOnSuccess(v -> {
                    sessionManager.broadcastInstantBuyCancelled(auctionId);
                    log.info("Instant buy reservation cancelled, auction restored: auctionId={}", auctionId);
                });
    }
}
