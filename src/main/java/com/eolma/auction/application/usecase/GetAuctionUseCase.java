package com.eolma.auction.application.usecase;

import com.eolma.auction.adapter.in.web.dto.AuctionListResponse;
import com.eolma.auction.adapter.in.web.dto.AuctionResponse;
import com.eolma.auction.adapter.in.web.dto.BidHistoryResponse;
import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.application.port.out.AuctionWishlistPort;
import com.eolma.auction.domain.model.Auction;
import com.eolma.auction.domain.repository.AuctionRepository;
import com.eolma.auction.domain.repository.BidRepository;
import com.eolma.auction.domain.service.AuctionService;
import com.eolma.common.dto.PageResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class GetAuctionUseCase {

    private final AuctionService auctionService;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionCachePort auctionCachePort;
    private final AuctionWishlistPort wishlistPort;

    public GetAuctionUseCase(AuctionService auctionService,
                              AuctionRepository auctionRepository,
                              BidRepository bidRepository,
                              AuctionCachePort auctionCachePort,
                              AuctionWishlistPort wishlistPort) {
        this.auctionService = auctionService;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.auctionCachePort = auctionCachePort;
        this.wishlistPort = wishlistPort;
    }

    public Mono<AuctionResponse> getAuction(Long auctionId, String userId) {
        return auctionService.findById(auctionId)
                .flatMap(auction ->
                        auctionCachePort.getAuctionState(auctionId)
                                .defaultIfEmpty(Map.of())
                                .flatMap(state -> {
                                    if (userId != null) {
                                        return wishlistPort.existsByAuctionIdAndUserId(auctionId, userId)
                                                .map(wishlisted -> toResponse(auction, state, wishlisted));
                                    }
                                    return Mono.just(toResponse(auction, state, null));
                                })
                );
    }

    public Mono<PageResponse<AuctionListResponse>> getAuctions(String status, String sort, int page, int size) {
        long offset = (long) page * size;
        boolean sortByBidCount = "bidCount".equals(sort);

        if (status == null || status.isBlank()) {
            var flux = sortByBidCount
                    ? auctionRepository.findAllAuctionsByBidCount(size, offset)
                    : auctionRepository.findAllAuctions(size, offset);
            return flux.concatMap(this::toListResponseWithWishlistCount)
                    .collectList()
                    .zipWith(auctionRepository.count())
                    .map(tuple -> {
                        long total = tuple.getT2();
                        int totalPages = (int) Math.ceil((double) total / size);
                        return PageResponse.of(tuple.getT1(), page, size, total, totalPages);
                    });
        }

        return auctionRepository.findByStatusPaged(status, size, offset)
                .concatMap(this::toListResponseWithWishlistCount)
                .collectList()
                .zipWith(auctionRepository.countByStatus(status))
                .map(tuple -> {
                    long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    return PageResponse.of(tuple.getT1(), page, size, total, totalPages);
                });
    }

    public Mono<PageResponse<AuctionListResponse>> getMyAuctions(String bidderId, int page, int size) {
        long offset = (long) page * size;

        return auctionRepository.findByBidderId(bidderId, size, offset)
                .concatMap(this::toListResponseWithWishlistCount)
                .collectList()
                .zipWith(auctionRepository.countByBidderId(bidderId))
                .map(tuple -> {
                    long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    return PageResponse.of(tuple.getT1(), page, size, total, totalPages);
                });
    }

    public Mono<PageResponse<BidHistoryResponse>> getBidHistory(Long auctionId, int page, int size) {
        long offset = (long) page * size;

        return bidRepository.findByAuctionId(auctionId, size, offset)
                .map(bid -> new BidHistoryResponse(
                        bid.getId(), bid.getBidderId(), bid.getAmount(),
                        bid.getBidType(), bid.getCreatedAt()))
                .collectList()
                .zipWith(bidRepository.countByAuctionId(auctionId))
                .map(tuple -> {
                    long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    return PageResponse.of(tuple.getT1(), page, size, total, totalPages);
                });
    }

    private AuctionResponse toResponse(Auction auction, Map<String, String> state, Boolean isWishlisted) {
        Long currentPrice = state.containsKey("currentPrice")
                ? Long.parseLong(state.get("currentPrice"))
                : auction.getCurrentPrice();
        int bidCount = state.containsKey("bidCount")
                ? Integer.parseInt(state.get("bidCount"))
                : auction.getBidCount();

        long remainingSeconds = 0;
        if (auction.isActive() && auction.getEndAt() != null) {
            remainingSeconds = Math.max(0, Duration.between(LocalDateTime.now(), auction.getEndAt()).getSeconds());
        }

        boolean instantBuyLocked = auction.isInstantBuyLocked(currentPrice);

        return new AuctionResponse(
                auction.getId(), auction.getProductId(), auction.getSellerId(),
                auction.getTitle(), auction.getStartingPrice(), auction.getInstantPrice(),
                auction.getInstantBuyLockPercent(), instantBuyLocked,
                auction.getReservePrice() != null, auction.getMinBidUnit(),
                currentPrice, bidCount, auction.getStatus(),
                auction.getEndAt(), remainingSeconds,
                auction.getWinnerId(), auction.getWinningPrice(),
                auction.getCreatedAt(), isWishlisted
        );
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
