package com.eolma.auction.domain.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("auction_wishlist")
@Getter
@Setter
public class AuctionWishlist {

    @Id
    private Long id;

    @Column("auction_id")
    private Long auctionId;

    @Column("user_id")
    private String userId;

    @Column("created_at")
    private LocalDateTime createdAt;

    public static AuctionWishlist create(Long auctionId, String userId) {
        AuctionWishlist wishlist = new AuctionWishlist();
        wishlist.setAuctionId(auctionId);
        wishlist.setUserId(userId);
        wishlist.setCreatedAt(LocalDateTime.now());
        return wishlist;
    }
}
