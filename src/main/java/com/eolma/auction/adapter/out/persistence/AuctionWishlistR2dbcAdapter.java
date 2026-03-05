package com.eolma.auction.adapter.out.persistence;

import com.eolma.auction.application.port.out.AuctionWishlistPort;
import com.eolma.auction.domain.model.AuctionWishlist;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AuctionWishlistR2dbcAdapter implements AuctionWishlistPort {

    private final AuctionWishlistRepository repository;

    public AuctionWishlistR2dbcAdapter(AuctionWishlistRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<AuctionWishlist> save(AuctionWishlist wishlist) {
        return repository.save(wishlist);
    }

    @Override
    public Mono<Void> deleteByAuctionIdAndUserId(Long auctionId, Long userId) {
        return repository.deleteByAuctionIdAndUserId(auctionId, userId);
    }

    @Override
    public Mono<AuctionWishlist> findByAuctionIdAndUserId(Long auctionId, Long userId) {
        return repository.findByAuctionIdAndUserId(auctionId, userId);
    }

    @Override
    public Flux<AuctionWishlist> findByUserId(Long userId, int limit, long offset) {
        return repository.findByUserId(userId, limit, offset);
    }

    @Override
    public Mono<Long> countByUserId(Long userId) {
        return repository.countByUserId(userId);
    }

    @Override
    public Mono<Long> countByAuctionId(Long auctionId) {
        return repository.countByAuctionId(auctionId);
    }

    @Override
    public Mono<Boolean> existsByAuctionIdAndUserId(Long auctionId, Long userId) {
        return repository.existsByAuctionIdAndUserId(auctionId, userId);
    }
}
