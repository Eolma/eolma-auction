package com.eolma.auction.infra;

import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.application.usecase.CancelInstantBuyUseCase;
import com.eolma.auction.domain.model.AuctionStatus;
import com.eolma.auction.domain.repository.AuctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InstantBuyExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstantBuyExpirationScheduler.class);
    private final AuctionRepository auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final CancelInstantBuyUseCase cancelInstantBuyUseCase;

    public InstantBuyExpirationScheduler(AuctionRepository auctionRepository,
                                          AuctionCachePort auctionCachePort,
                                          CancelInstantBuyUseCase cancelInstantBuyUseCase) {
        this.auctionRepository = auctionRepository;
        this.auctionCachePort = auctionCachePort;
        this.cancelInstantBuyUseCase = cancelInstantBuyUseCase;
    }

    @Scheduled(fixedRate = 5000)
    public void checkExpiredReservations() {
        auctionRepository.findByStatus(AuctionStatus.PENDING_INSTANT_BUY.name())
                .flatMap(auction ->
                        auctionCachePort.getInstantBuyReservation(auction.getId())
                                .flatMap(reservation -> {
                                    if (reservation.isEmpty()) {
                                        log.info("Instant buy reservation expired, restoring auction: auctionId={}", auction.getId());
                                        return cancelInstantBuyUseCase.execute(auction.getId(), null);
                                    }
                                    return reactor.core.publisher.Mono.empty();
                                })
                )
                .subscribe(
                        null,
                        error -> log.error("Error checking expired instant buy reservations", error)
                );
    }
}
