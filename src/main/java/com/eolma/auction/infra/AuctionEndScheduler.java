package com.eolma.auction.infra;

import com.eolma.auction.adapter.out.redis.AuctionRedisAdapter;
import com.eolma.auction.application.usecase.CloseAuctionUseCase;
import com.eolma.auction.domain.model.AuctionStatus;
import com.eolma.auction.domain.repository.AuctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuctionEndScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionEndScheduler.class);
    private final AuctionRedisAdapter auctionRedisAdapter;
    private final AuctionRepository auctionRepository;
    private final CloseAuctionUseCase closeAuctionUseCase;

    public AuctionEndScheduler(AuctionRedisAdapter auctionRedisAdapter,
                                AuctionRepository auctionRepository,
                                CloseAuctionUseCase closeAuctionUseCase) {
        this.auctionRedisAdapter = auctionRedisAdapter;
        this.auctionRepository = auctionRepository;
        this.closeAuctionUseCase = closeAuctionUseCase;
    }

    // Redis 기반 만료 체크 (1초 간격)
    @Scheduled(fixedRate = 1000)
    public void checkExpiredAuctions() {
        auctionRedisAdapter.getExpiredAuctionIds()
                .subscribe(auctionIds -> {
                    if (!auctionIds.isEmpty()) {
                        log.info("Found {} expired auctions from Redis", auctionIds.size());
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

    // DB 기반 만료 체크 폴백 (30초 간격, Redis에 등록 안 된 경매 처리)
    @Scheduled(fixedRate = 30000)
    public void checkExpiredAuctionsFromDb() {
        auctionRepository.findExpiredAuctions(AuctionStatus.ACTIVE.name())
                .subscribe(auction -> {
                    log.info("Found expired auction from DB fallback: auctionId={}", auction.getId());
                    closeAuctionUseCase.execute(auction.getId())
                            .subscribe(
                                    null,
                                    error -> log.error("Failed to close auction (DB fallback): auctionId={}", auction.getId(), error)
                            );
                });
    }
}
