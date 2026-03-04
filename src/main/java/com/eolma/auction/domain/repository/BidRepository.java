package com.eolma.auction.domain.repository;

import com.eolma.auction.domain.model.Bid;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BidRepository extends ReactiveCrudRepository<Bid, Long> {

    Flux<Bid> findByAuctionIdOrderByAmountDesc(Long auctionId);

    @Query("SELECT * FROM bid WHERE auction_id = :auctionId ORDER BY amount DESC LIMIT :limit OFFSET :offset")
    Flux<Bid> findByAuctionId(Long auctionId, int limit, long offset);

    @Query("SELECT COUNT(*) FROM bid WHERE auction_id = :auctionId")
    Mono<Long> countByAuctionId(Long auctionId);

    @Query("SELECT * FROM bid WHERE auction_id = :auctionId ORDER BY amount DESC LIMIT 1")
    Mono<Bid> findTopBid(Long auctionId);
}
