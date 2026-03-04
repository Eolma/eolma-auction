package com.eolma.auction.adapter.in.web;

import com.eolma.auction.adapter.in.web.dto.AuctionListResponse;
import com.eolma.auction.adapter.in.web.dto.AuctionResponse;
import com.eolma.auction.adapter.in.web.dto.BidHistoryResponse;
import com.eolma.auction.application.usecase.GetAuctionUseCase;
import com.eolma.common.dto.PageResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final GetAuctionUseCase getAuctionUseCase;

    public AuctionController(GetAuctionUseCase getAuctionUseCase) {
        this.getAuctionUseCase = getAuctionUseCase;
    }

    @GetMapping
    public Mono<PageResponse<AuctionListResponse>> getAuctions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getAuctionUseCase.getAuctions(status, page, size);
    }

    @GetMapping("/me")
    public Mono<PageResponse<AuctionListResponse>> getMyAuctions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getAuctionUseCase.getMyAuctions(userId, page, size);
    }

    @GetMapping("/{id}")
    public Mono<AuctionResponse> getAuction(@PathVariable Long id) {
        return getAuctionUseCase.getAuction(id);
    }

    @GetMapping("/{id}/bids")
    public Mono<PageResponse<BidHistoryResponse>> getBidHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getAuctionUseCase.getBidHistory(id, page, size);
    }
}
