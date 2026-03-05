package com.eolma.auction.adapter.out.persistence;

import com.eolma.auction.domain.model.AuctionWishlist;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuctionWishlistRepository extends ReactiveCrudRepository<AuctionWishlist, Long> {

    Mono<AuctionWishlist> findByAuctionIdAndUserId(Long auctionId, Long userId);

    Mono<Void> deleteByAuctionIdAndUserId(Long auctionId, Long userId);

    @Query("SELECT * FROM auction_wishlist WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<AuctionWishlist> findByUserId(Long userId, int limit, long offset);

    Mono<Long> countByUserId(Long userId);

    Mono<Long> countByAuctionId(Long auctionId);

    Mono<Boolean> existsByAuctionIdAndUserId(Long auctionId, Long userId);
}
