package com.eolma.auction.domain.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("bid")
@Getter
@Setter
public class Bid {

    @Id
    private Long id;

    @Column("auction_id")
    private Long auctionId;

    @Column("bidder_id")
    private String bidderId;

    private Long amount;

    @Column("bid_type")
    private String bidType;

    private String status;

    @Column("created_at")
    private LocalDateTime createdAt;

    public BidType getBidTypeEnum() {
        return BidType.valueOf(bidType);
    }

    public BidStatus getBidStatus() {
        return BidStatus.valueOf(status);
    }

    public static Bid create(Long auctionId, String bidderId, Long amount, BidType type) {
        Bid bid = new Bid();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bid.setBidType(type.name());
        bid.setStatus(BidStatus.ACCEPTED.name());
        bid.setCreatedAt(LocalDateTime.now());
        return bid;
    }
}
