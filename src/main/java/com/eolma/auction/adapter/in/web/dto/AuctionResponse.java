package com.eolma.auction.adapter.in.web.dto;

import java.time.LocalDateTime;

public record AuctionResponse(
        Long id,
        Long productId,
        String sellerId,
        String title,
        Long startingPrice,
        Long instantPrice,
        Integer instantBuyLockPercent,
        boolean instantBuyLocked,
        boolean hasReservePrice,
        Long minBidUnit,
        Long currentPrice,
        int bidCount,
        String status,
        LocalDateTime endAt,
        long remainingSeconds,
        String winnerId,
        Long winningPrice,
        LocalDateTime createdAt,
        Boolean isWishlisted
) {
}
