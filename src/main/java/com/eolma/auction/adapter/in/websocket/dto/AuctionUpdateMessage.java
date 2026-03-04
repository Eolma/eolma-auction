package com.eolma.auction.adapter.in.websocket.dto;

public record AuctionUpdateMessage(
        String type,
        Long currentPrice,
        int bidCount,
        long remainingSeconds,
        String status
) {

    public static AuctionUpdateMessage update(Long currentPrice, int bidCount, long remainingSeconds) {
        return new AuctionUpdateMessage("AUCTION_UPDATE", currentPrice, bidCount, remainingSeconds, "ACTIVE");
    }

    public static AuctionUpdateMessage closed(Long winnerId, Long winningPrice, String status) {
        return new AuctionUpdateMessage("AUCTION_CLOSED", winningPrice, 0, 0, status);
    }
}
