package com.eolma.auction.adapter.in.websocket.dto;

public record AuctionUpdateMessage(
        String type,
        Long currentPrice,
        int bidCount,
        long remainingSeconds,
        String status,
        Integer viewerCount,
        String bidderNickname
) {

    public static AuctionUpdateMessage update(Long currentPrice, int bidCount, long remainingSeconds,
                                               int viewerCount, String bidderNickname) {
        return new AuctionUpdateMessage("AUCTION_UPDATE", currentPrice, bidCount, remainingSeconds,
                "ACTIVE", viewerCount, bidderNickname);
    }

    public static AuctionUpdateMessage closed(Long winnerId, Long winningPrice, String status) {
        return new AuctionUpdateMessage("AUCTION_CLOSED", winningPrice, 0, 0, status, null, null);
    }
}
