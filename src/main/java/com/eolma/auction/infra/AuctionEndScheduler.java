package com.eolma.auction.infra;

import com.eolma.auction.adapter.out.redis.AuctionRedisAdapter;
import com.eolma.auction.application.usecase.CloseAuctionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuctionEndScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionEndScheduler.class);
    private final AuctionRedisAdapter auctionRedisAdapter;
    private final CloseAuctionUseCase closeAuctionUseCase;

    public AuctionEndScheduler(AuctionRedisAdapter auctionRedisAdapter,
                                CloseAuctionUseCase closeAuctionUseCase) {
        this.auctionRedisAdapter = auctionRedisAdapter;
        this.closeAuctionUseCase = closeAuctionUseCase;
    }

    @Scheduled(fixedRate = 1000)
    public void checkExpiredAuctions() {
        auctionRedisAdapter.getExpiredAuctionIds()
                .subscribe(auctionIds -> {
                    if (!auctionIds.isEmpty()) {
                        log.info("Found {} expired auctions", auctionIds.size());
                        auctionIds.forEach(idStr -> {
                            Long auctionId = Long.parseLong(idStr);
                            closeAuctionUseCase.execute(auctionId)
                                    .subscribe(
                                            null,
                                            error -> log.error("Failed to close auction: auctionId={}", auctionId, error)
                                    );
                        });
                    }
                });
    }
}
