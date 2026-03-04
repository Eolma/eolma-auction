package com.eolma.auction.domain.service;

import com.eolma.auction.domain.model.AuctionStatus;
import com.eolma.auction.domain.model.BidResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BidValidationService {

    private static final Logger log = LoggerFactory.getLogger(BidValidationService.class);

    public BidResult validate(Long bidderId, Long sellerId, Long bidAmount,
                               Long currentPrice, Long minBidUnit, String status,
                               Long currentWinnerId) {

        if (!AuctionStatus.ACTIVE.name().equals(status)) {
            log.warn("Bid rejected: auction not active, status={}", status);
            return BidResult.failure("AUCTION_CLOSED", "Auction is not active");
        }

        if (bidderId.equals(sellerId)) {
            log.warn("Bid rejected: seller cannot bid on own auction, bidderId={}", bidderId);
            return BidResult.failure("SELF_BID", "Cannot bid on your own auction");
        }

        if (bidderId.equals(currentWinnerId)) {
            log.warn("Bid rejected: already highest bidder, bidderId={}", bidderId);
            return BidResult.failure("ALREADY_HIGHEST", "You are already the highest bidder");
        }

        long requiredMinBid = currentPrice + minBidUnit;
        if (bidAmount < requiredMinBid) {
            log.warn("Bid rejected: too low, bidAmount={}, required={}", bidAmount, requiredMinBid);
            return BidResult.failure("BID_TOO_LOW",
                    "Bid must be at least " + requiredMinBid);
        }

        return null;
    }
}
