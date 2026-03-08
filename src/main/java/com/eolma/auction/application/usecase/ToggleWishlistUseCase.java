package com.eolma.auction.application.usecase;

import com.eolma.auction.application.port.out.AuctionWishlistPort;
import com.eolma.auction.domain.model.AuctionWishlist;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ToggleWishlistUseCase {

    private final AuctionWishlistPort wishlistPort;

    public ToggleWishlistUseCase(AuctionWishlistPort wishlistPort) {
        this.wishlistPort = wishlistPort;
    }

    public Mono<Boolean> execute(Long auctionId, String userId) {
        return wishlistPort.findByAuctionIdAndUserId(auctionId, userId)
                .flatMap(existing ->
                        wishlistPort.deleteByAuctionIdAndUserId(auctionId, userId)
                                .thenReturn(false)
                )
                .switchIfEmpty(
                        wishlistPort.save(AuctionWishlist.create(auctionId, userId))
                                .thenReturn(true)
                );
    }
}
