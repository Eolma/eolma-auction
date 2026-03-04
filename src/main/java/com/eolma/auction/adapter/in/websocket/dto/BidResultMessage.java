package com.eolma.auction.adapter.in.websocket.dto;

public record BidResultMessage(
        String type,
        String status,
        Long currentPrice,
        int bidCount,
        Long nextMinBid,
        String errorCode,
        String message
) {

    public static BidResultMessage success(Long currentPrice, int bidCount, Long nextMinBid) {
        return new BidResultMessage("BID_RESULT", "ACCEPTED", currentPrice, bidCount, nextMinBid, null, null);
    }

    public static BidResultMessage failure(String errorCode, String message) {
        return new BidResultMessage("BID_RESULT", "REJECTED", null, 0, null, errorCode, message);
    }
}
