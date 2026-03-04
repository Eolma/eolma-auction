package com.eolma.auction.adapter.in.websocket.dto;

public record BidMessage(
        String type,
        Long amount
) {
}
