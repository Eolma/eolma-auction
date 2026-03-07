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
            return BidResult.failure("AUCTION_CLOSED", "경매가 종료되었습니다.");
        }

        if (bidderId.equals(sellerId)) {
            log.warn("Bid rejected: seller cannot bid on own auction, bidderId={}", bidderId);
            return BidResult.failure("SELF_BID", "본인의 경매에는 입찰할 수 없습니다.");
        }

        if (bidderId.equals(currentWinnerId)) {
            log.warn("Bid rejected: already highest bidder, bidderId={}", bidderId);
            return BidResult.failure("ALREADY_HIGHEST", "이미 최고 입찰자입니다.");
        }

        long requiredMinBid = currentPrice + minBidUnit;
        if (bidAmount < requiredMinBid) {
            log.warn("Bid rejected: too low, bidAmount={}, required={}", bidAmount, requiredMinBid);
            return BidResult.failure("BID_TOO_LOW",
                    "최소 " + String.format("%,d", requiredMinBid) + "원 이상 입찰해야 합니다.");
        }

        return null;
    }
}
