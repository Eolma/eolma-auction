package com.eolma.auction.domain.repository;

import com.eolma.auction.domain.model.Auction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuctionRepository extends ReactiveCrudRepository<Auction, Long> {

    Mono<Auction> findByProductId(Long productId);

    Flux<Auction> findByStatus(String status);

    @Query("SELECT * FROM auction WHERE status = :status AND end_at <= NOW() ORDER BY end_at ASC")
    Flux<Auction> findExpiredAuctions(String status);

    @Query("SELECT * FROM auction WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Auction> findActiveAuctions(int limit, long offset);

    @Query("SELECT COUNT(*) FROM auction WHERE status = 'ACTIVE'")
    Mono<Long> countActiveAuctions();

    @Query("SELECT * FROM auction ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Auction> findAllAuctions(int limit, long offset);

    @Query("SELECT * FROM auction WHERE status = :status ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Auction> findByStatusPaged(String status, int limit, long offset);

    @Query("SELECT COUNT(*) FROM auction WHERE status = :status")
    Mono<Long> countByStatus(String status);

    @Query("""
            SELECT a.* FROM auction a
            INNER JOIN bid b ON a.id = b.auction_id
            WHERE b.bidder_id = :bidderId
            GROUP BY a.id
            ORDER BY MAX(b.created_at) DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<Auction> findByBidderId(Long bidderId, int limit, long offset);

    @Query("""
            SELECT COUNT(DISTINCT a.id) FROM auction a
            INNER JOIN bid b ON a.id = b.auction_id
            WHERE b.bidder_id = :bidderId
            """)
    Mono<Long> countByBidderId(Long bidderId);
}
