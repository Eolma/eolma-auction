package com.eolma.auction.domain.model;

public record BidResult(
        boolean accepted,
        Long bidId,
        Long currentPrice,
        int bidCount,
        Long nextMinBid,
        String errorCode,
        String errorMessage,
        boolean instantBuy
) {

    public static BidResult success(Long bidId, Long currentPrice, int bidCount, Long minBidUnit) {
        return new BidResult(true, bidId, currentPrice, bidCount, currentPrice + minBidUnit, null, null, false);
    }

    public static BidResult successInstantBuy(Long bidId, Long currentPrice, int bidCount, Long minBidUnit) {
        return new BidResult(true, bidId, currentPrice, bidCount, currentPrice + minBidUnit, null, null, true);
    }

    public static BidResult failure(String errorCode, String errorMessage) {
        return new BidResult(false, null, null, 0, null, errorCode, errorMessage, false);
    }
}
