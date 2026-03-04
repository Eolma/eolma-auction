package com.eolma.auction.domain.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("auction")
@Getter
@Setter
public class Auction {

    @Id
    private Long id;

    @Column("product_id")
    private Long productId;

    @Column("seller_id")
    private Long sellerId;

    private String title;

    @Column("starting_price")
    private Long startingPrice;

    @Column("instant_price")
    private Long instantPrice;

    @Column("reserve_price")
    private Long reservePrice;

    @Column("min_bid_unit")
    private Long minBidUnit;

    @Column("current_price")
    private Long currentPrice;

    @Column("bid_count")
    private int bidCount;

    @Column("end_type")
    private String endType;

    @Column("max_bid_count")
    private Integer maxBidCount;

    private String status;

    @Column("end_at")
    private LocalDateTime endAt;

    @Column("winner_id")
    private Long winnerId;

    @Column("winning_price")
    private Long winningPrice;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public AuctionStatus getAuctionStatus() {
        return AuctionStatus.valueOf(status);
    }

    public void setAuctionStatus(AuctionStatus auctionStatus) {
        this.status = auctionStatus.name();
    }

    public AuctionEndType getAuctionEndType() {
        return AuctionEndType.valueOf(endType);
    }

    public void setAuctionEndType(AuctionEndType auctionEndType) {
        this.endType = auctionEndType.name();
    }

    public boolean isActive() {
        return AuctionStatus.ACTIVE.name().equals(status);
    }

    public boolean hasInstantPrice() {
        return instantPrice != null && instantPrice > 0;
    }

    public boolean hasReservePrice() {
        return reservePrice != null && reservePrice > 0;
    }

    public static Auction create(Long productId, Long sellerId, String title,
                                  Long startingPrice, Long instantPrice, Long reservePrice,
                                  Long minBidUnit, AuctionEndType endType, String endValue) {
        Auction auction = new Auction();
        auction.setProductId(productId);
        auction.setSellerId(sellerId);
        auction.setTitle(title);
        auction.setStartingPrice(startingPrice);
        auction.setInstantPrice(instantPrice);
        auction.setReservePrice(reservePrice);
        auction.setMinBidUnit(minBidUnit);
        auction.setCurrentPrice(startingPrice);
        auction.setBidCount(0);
        auction.setAuctionEndType(endType);
        auction.setAuctionStatus(AuctionStatus.ACTIVE);
        auction.setCreatedAt(LocalDateTime.now());
        auction.setUpdatedAt(LocalDateTime.now());

        if (endType == AuctionEndType.TIMED) {
            long hours = Long.parseLong(endValue);
            auction.setEndAt(LocalDateTime.now().plusHours(hours));
        } else if (endType == AuctionEndType.BID_COUNT) {
            auction.setMaxBidCount(Integer.parseInt(endValue));
            auction.setEndAt(LocalDateTime.now().plusDays(7));
        }

        return auction;
    }
}
