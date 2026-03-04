package com.eolma.auction.adapter.in.web.dto;

import java.time.LocalDateTime;

public record AuctionListResponse(
        Long id,
        String title,
        Long currentPrice,
        int bidCount,
        String status,
        LocalDateTime endAt,
        long remainingSeconds
) {
}
