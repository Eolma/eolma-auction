package com.eolma.auction.application.port.out;

import com.eolma.auction.domain.model.AuctionWishlist;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuctionWishlistPort {

    Mono<AuctionWishlist> save(AuctionWishlist wishlist);

    Mono<Void> deleteByAuctionIdAndUserId(Long auctionId, Long userId);

    Mono<AuctionWishlist> findByAuctionIdAndUserId(Long auctionId, Long userId);

    Flux<AuctionWishlist> findByUserId(Long userId, int limit, long offset);

    Mono<Long> countByUserId(Long userId);

    Mono<Long> countByAuctionId(Long auctionId);

    Mono<Boolean> existsByAuctionIdAndUserId(Long auctionId, Long userId);
}
