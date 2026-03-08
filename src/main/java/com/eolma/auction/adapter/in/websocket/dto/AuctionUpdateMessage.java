package com.eolma.auction.adapter.in.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuctionUpdateMessage(
        String type,
        Long currentPrice,
        Integer bidCount,
        Long remainingSeconds,
        String status,
        Integer viewerCount,
        String bidderNickname,
        String buyerId,
        String expiresAt
) {

    public static AuctionUpdateMessage update(Long currentPrice, int bidCount, long remainingSeconds,
                                               int viewerCount, String bidderNickname) {
        return new AuctionUpdateMessage("AUCTION_UPDATE", currentPrice, bidCount, remainingSeconds,
                "ACTIVE", viewerCount, bidderNickname, null, null);
    }

    public static AuctionUpdateMessage closed(String winnerId, Long winningPrice, String status) {
        return new AuctionUpdateMessage("AUCTION_CLOSED", winningPrice, 0, 0L, status, null, null, null, null);
    }

    public static AuctionUpdateMessage instantBuyStarted(String buyerId, String expiresAt) {
        return new AuctionUpdateMessage("INSTANT_BUY_STARTED", null, null, null,
                "PENDING_INSTANT_BUY", null, null, buyerId, expiresAt);
    }

    public static AuctionUpdateMessage instantBuyCancelled() {
        return new AuctionUpdateMessage("INSTANT_BUY_CANCELLED", null, null, null,
                "ACTIVE", null, null, null, null);
    }

    public static AuctionUpdateMessage instantBuyLocked() {
        return new AuctionUpdateMessage("INSTANT_BUY_LOCKED", null, null, null,
                "ACTIVE", null, null, null, null);
    }
}
