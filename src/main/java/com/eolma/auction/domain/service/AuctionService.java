package com.eolma.auction.domain.service;

import com.eolma.auction.domain.model.Auction;
import com.eolma.auction.domain.model.AuctionEndType;
import com.eolma.auction.domain.model.AuctionStatus;
import com.eolma.auction.domain.repository.AuctionRepository;
import com.eolma.common.exception.EolmaException;
import com.eolma.common.exception.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);
    private final AuctionRepository auctionRepository;

    public AuctionService(AuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
    }

    public Mono<Auction> createAuction(Long productId, Long sellerId, String title,
                                        Long startingPrice, Long instantPrice, Long reservePrice,
                                        Long minBidUnit, String endType, String endValue) {
        AuctionEndType auctionEndType = AuctionEndType.valueOf(endType);
        Auction auction = Auction.create(productId, sellerId, title, startingPrice,
                instantPrice, reservePrice, minBidUnit, auctionEndType, endValue);

        return auctionRepository.save(auction)
                .doOnSuccess(saved -> log.info("Auction created: auctionId={}, productId={}", saved.getId(), productId));
    }

    public Mono<Auction> findById(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .switchIfEmpty(Mono.error(new EolmaException(ErrorType.AUCTION_NOT_FOUND,
                        "Auction not found: " + auctionId)));
    }

    public Mono<Auction> completeAuction(Long auctionId, Long winnerId, Long winningPrice) {
        return findById(auctionId)
                .flatMap(auction -> {
                    auction.setAuctionStatus(AuctionStatus.COMPLETED);
                    auction.setWinnerId(winnerId);
                    auction.setWinningPrice(winningPrice);
                    auction.setUpdatedAt(java.time.LocalDateTime.now());
                    return auctionRepository.save(auction);
                })
                .doOnSuccess(a -> log.info("Auction completed: auctionId={}, winnerId={}, price={}",
                        auctionId, winnerId, winningPrice));
    }

    public Mono<Auction> failAuction(Long auctionId) {
        return findById(auctionId)
                .flatMap(auction -> {
                    auction.setAuctionStatus(AuctionStatus.FAILED);
                    auction.setUpdatedAt(java.time.LocalDateTime.now());
                    return auctionRepository.save(auction);
                })
                .doOnSuccess(a -> log.info("Auction failed: auctionId={}", auctionId));
    }

    public Mono<Auction> updateBidInfo(Long auctionId, Long currentPrice, int bidCount) {
        return findById(auctionId)
                .flatMap(auction -> {
                    auction.setCurrentPrice(currentPrice);
                    auction.setBidCount(bidCount);
                    auction.setUpdatedAt(java.time.LocalDateTime.now());
                    return auctionRepository.save(auction);
                });
    }
}
