package com.eolma.auction.application.port.out;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

public interface AuctionCachePort {

    Mono<Void> initAuctionCache(Long auctionId, Long sellerId, Long currentPrice,
                                Long minBidUnit, Long instantPrice, LocalDateTime endAt);

    Mono<Map<String, String>> getAuctionState(Long auctionId);

    Mono<Void> updateBidState(Long auctionId, Long currentPrice, Long winnerId, int bidCount);

    Mono<Void> addBidToRanking(Long auctionId, Long bidderId, Long amount);

    Mono<Void> scheduleEnding(Long auctionId, LocalDateTime endAt);

    Mono<Void> removeFromEnding(Long auctionId);

    Mono<Void> cleanupAuctionCache(Long auctionId);

    Mono<Void> patchField(Long auctionId, String field, String value);
}
