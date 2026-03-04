package com.eolma.auction.application.usecase;

import com.eolma.auction.adapter.in.web.dto.AuctionListResponse;
import com.eolma.auction.adapter.in.web.dto.AuctionResponse;
import com.eolma.auction.adapter.in.web.dto.BidHistoryResponse;
import com.eolma.auction.application.port.out.AuctionCachePort;
import com.eolma.auction.domain.model.Auction;
import com.eolma.auction.domain.model.AuctionStatus;
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

    public GetAuctionUseCase(AuctionService auctionService,
                              AuctionRepository auctionRepository,
                              BidRepository bidRepository,
                              AuctionCachePort auctionCachePort) {
        this.auctionService = auctionService;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.auctionCachePort = auctionCachePort;
    }

    public Mono<AuctionResponse> getAuction(Long auctionId) {
        return auctionService.findById(auctionId)
                .flatMap(auction ->
                        auctionCachePort.getAuctionState(auctionId)
                                .defaultIfEmpty(Map.of())
                                .map(state -> toResponse(auction, state))
                );
    }

    public Mono<PageResponse<AuctionListResponse>> getActiveAuctions(int page, int size) {
        long offset = (long) page * size;

        return auctionRepository.findActiveAuctions(size, offset)
                .map(this::toListResponse)
                .collectList()
                .zipWith(auctionRepository.countActiveAuctions())
                .map(tuple -> {
                    long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    return PageResponse.of(tuple.getT1(), page, size, total, totalPages);
                });
    }

    public Mono<PageResponse<AuctionListResponse>> getMyAuctions(Long bidderId, int page, int size) {
        long offset = (long) page * size;

        return auctionRepository.findByBidderId(bidderId, size, offset)
                .map(this::toListResponse)
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

    private AuctionResponse toResponse(Auction auction, Map<String, String> state) {
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

        return new AuctionResponse(
                auction.getId(), auction.getProductId(), auction.getSellerId(),
                auction.getTitle(), auction.getStartingPrice(), auction.getInstantPrice(),
                auction.getReservePrice() != null, auction.getMinBidUnit(),
                currentPrice, bidCount, auction.getStatus(),
                auction.getEndAt(), remainingSeconds,
                auction.getWinnerId(), auction.getWinningPrice(),
                auction.getCreatedAt()
        );
    }

    private AuctionListResponse toListResponse(Auction auction) {
        long remainingSeconds = 0;
        if (auction.isActive() && auction.getEndAt() != null) {
            remainingSeconds = Math.max(0, Duration.between(LocalDateTime.now(), auction.getEndAt()).getSeconds());
        }

        return new AuctionListResponse(
                auction.getId(), auction.getTitle(), auction.getCurrentPrice(),
                auction.getBidCount(), auction.getStatus(), auction.getEndAt(),
                remainingSeconds
        );
    }
}
