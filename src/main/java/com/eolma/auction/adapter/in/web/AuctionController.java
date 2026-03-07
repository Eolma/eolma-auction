package com.eolma.auction.adapter.in.web;

import com.eolma.auction.adapter.in.web.dto.AuctionListResponse;
import com.eolma.auction.adapter.in.web.dto.AuctionResponse;
import com.eolma.auction.adapter.in.web.dto.BidHistoryResponse;
import com.eolma.auction.application.usecase.CancelInstantBuyUseCase;
import com.eolma.auction.application.usecase.GetAuctionUseCase;
import com.eolma.common.dto.PageResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final GetAuctionUseCase getAuctionUseCase;
    private final CancelInstantBuyUseCase cancelInstantBuyUseCase;

    public AuctionController(GetAuctionUseCase getAuctionUseCase,
                              CancelInstantBuyUseCase cancelInstantBuyUseCase) {
        this.getAuctionUseCase = getAuctionUseCase;
        this.cancelInstantBuyUseCase = cancelInstantBuyUseCase;
    }

    @GetMapping
    public Mono<PageResponse<AuctionListResponse>> getAuctions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getAuctionUseCase.getAuctions(status, sort, page, size);
    }

    @GetMapping("/me")
    public Mono<PageResponse<AuctionListResponse>> getMyAuctions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getAuctionUseCase.getMyAuctions(userId, page, size);
    }

    @GetMapping("/{id}")
    public Mono<AuctionResponse> getAuction(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return getAuctionUseCase.getAuction(id, userId);
    }

    @GetMapping("/{id}/bids")
    public Mono<PageResponse<BidHistoryResponse>> getBidHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getAuctionUseCase.getBidHistory(id, page, size);
    }

    @PostMapping("/{id}/instant-buy/cancel")
    public Mono<Void> cancelInstantBuy(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return cancelInstantBuyUseCase.execute(id, userId);
    }
}
