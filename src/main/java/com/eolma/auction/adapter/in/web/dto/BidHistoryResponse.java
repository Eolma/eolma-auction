package com.eolma.auction.adapter.in.web.dto;

import java.time.LocalDateTime;

public record BidHistoryResponse(
        Long id,
        Long bidderId,
        Long amount,
        String bidType,
        LocalDateTime createdAt
) {
}
