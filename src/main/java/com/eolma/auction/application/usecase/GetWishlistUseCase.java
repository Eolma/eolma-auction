package com.eolma.auction.application.usecase;

import com.eolma.auction.adapter.in.web.dto.AuctionListResponse;
import com.eolma.auction.application.port.out.AuctionWishlistPort;
import com.eolma.auction.domain.model.Auction;
import com.eolma.auction.domain.service.AuctionService;
import com.eolma.common.dto.PageResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class GetWishlistUseCase {

    private final AuctionWishlistPort wishlistPort;
    private final AuctionService auctionService;

    public GetWishlistUseCase(AuctionWishlistPort wishlistPort, AuctionService auctionService) {
        this.wishlistPort = wishlistPort;
        this.auctionService = auctionService;
    }

    public Mono<Boolean> isWishlisted(Long auctionId, Long userId) {
        return wishlistPort.existsByAuctionIdAndUserId(auctionId, userId);
    }

    public Mono<PageResponse<AuctionListResponse>> getMyWishlist(Long userId, int page, int size) {
        long offset = (long) page * size;

        return wishlistPort.findByUserId(userId, size, offset)
                .concatMap(wishlist -> auctionService.findById(wishlist.getAuctionId())
                        .flatMap(this::toListResponseWithWishlistCount))
                .collectList()
                .zipWith(wishlistPort.countByUserId(userId))
                .map(tuple -> {
                    long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    return PageResponse.of(tuple.getT1(), page, size, total, totalPages);
                });
    }

    public Mono<Long> getWishlistCount(Long auctionId) {
        return wishlistPort.countByAuctionId(auctionId);
    }

    private Mono<AuctionListResponse> toListResponseWithWishlistCount(Auction auction) {
        long remainingSeconds = 0;
        if (auction.isActive() && auction.getEndAt() != null) {
            remainingSeconds = Math.max(0, Duration.between(LocalDateTime.now(), auction.getEndAt()).getSeconds());
        }

        final long rs = remainingSeconds;
        return wishlistPort.countByAuctionId(auction.getId())
                .defaultIfEmpty(0L)
                .map(count -> new AuctionListResponse(
                        auction.getId(), auction.getTitle(), auction.getCurrentPrice(),
                        auction.getBidCount(), auction.getStatus(), auction.getEndAt(),
                        rs, count
                ));
    }
}
