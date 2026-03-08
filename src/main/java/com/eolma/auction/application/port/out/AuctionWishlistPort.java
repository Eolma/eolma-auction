package com.eolma.auction.application.port.out;

import com.eolma.auction.domain.model.AuctionWishlist;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuctionWishlistPort {

    Mono<AuctionWishlist> save(AuctionWishlist wishlist);

    Mono<Void> deleteByAuctionIdAndUserId(Long auctionId, String userId);

    Mono<AuctionWishlist> findByAuctionIdAndUserId(Long auctionId, String userId);

    Flux<AuctionWishlist> findByUserId(String userId, int limit, long offset);

    Mono<Long> countByUserId(String userId);

    Mono<Long> countByAuctionId(Long auctionId);

    Mono<Boolean> existsByAuctionIdAndUserId(Long auctionId, String userId);
}
