package com.eolma.auction.adapter.in.web.dto;

import java.time.LocalDateTime;

public record AuctionResponse(
        Long id,
        Long productId,
        Long sellerId,
        String title,
        Long startingPrice,
        Long instantPrice,
        boolean hasReservePrice,
        Long minBidUnit,
        Long currentPrice,
        int bidCount,
        String status,
        LocalDateTime endAt,
        long remainingSeconds,
        Long winnerId,
        Long winningPrice,
        LocalDateTime createdAt,
        Boolean isWishlisted
) {
}
