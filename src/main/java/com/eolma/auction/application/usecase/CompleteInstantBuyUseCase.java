package com.eolma.auction.application.usecase;

import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.application.port.out.DistributedLockPort;
import com.eolma.auction.domain.service.AuctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class CompleteInstantBuyUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteInstantBuyUseCase.class);
    private final AuctionCachePort auctionCachePort;
    private final DistributedLockPort distributedLockPort;
    private final AuctionService auctionService;
    private final CloseAuctionUseCase closeAuctionUseCase;

    public CompleteInstantBuyUseCase(AuctionCachePort auctionCachePort,
                                     DistributedLockPort distributedLockPort,
                                     AuctionService auctionService,
                                     CloseAuctionUseCase closeAuctionUseCase) {
        this.auctionCachePort = auctionCachePort;
        this.distributedLockPort = distributedLockPort;
        this.auctionService = auctionService;
        this.closeAuctionUseCase = closeAuctionUseCase;
    }

    public Mono<Void> execute(Long auctionId, String buyerId, Long amount) {
        log.info("Completing instant buy: auctionId={}, buyerId={}, amount={}", auctionId, buyerId, amount);

        return Mono.fromCallable(() -> {
            distributedLockPort.executeWithLock("lock:auction:" + auctionId, () -> {
                completeInstantBuy(auctionId, buyerId, amount).block();
                return null;
            });
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Void> completeInstantBuy(Long auctionId, String buyerId, Long amount) {
        return auctionCachePort.getInstantBuyReservation(auctionId)
                .flatMap(reservation -> {
                    if (reservation.isEmpty()) {
                        log.warn("No reservation found for completion: auctionId={}", auctionId);
                        return Mono.empty();
                    }
                    // 캐시+DB를 ACTIVE로 복원한 뒤 closeAuction 호출
                    return auctionCachePort.updateBidState(auctionId, amount, buyerId, 0)
                            .then(auctionCachePort.removeInstantBuyReservation(auctionId))
                            .then(auctionCachePort.patchField(auctionId, "status", "ACTIVE"))
                            .then(auctionService.restoreActive(auctionId))
                            .then(closeAuctionUseCase.executeWithinLock(auctionId));
                });
    }
}
