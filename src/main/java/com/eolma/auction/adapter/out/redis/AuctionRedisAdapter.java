package com.eolma.auction.adapter.out.redis;

import com.eolma.auction.application.port.out.AuctionCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuctionRedisAdapter implements AuctionCachePort {

    private static final Logger log = LoggerFactory.getLogger(AuctionRedisAdapter.class);
    private static final Duration CACHE_TTL = Duration.ofHours(25);
    private static final String ENDING_KEY = "auction:ending";

    private final ReactiveStringRedisTemplate redisTemplate;

    public AuctionRedisAdapter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> initAuctionCache(Long auctionId, Long sellerId, Long currentPrice,
                                        Long minBidUnit, Long instantPrice, LocalDateTime endAt) {
        String key = auctionCurrentKey(auctionId);
        Map<String, String> fields = new HashMap<>();
        fields.put("currentPrice", String.valueOf(currentPrice));
        fields.put("winnerId", "0");
        fields.put("bidCount", "0");
        fields.put("status", "ACTIVE");
        fields.put("sellerId", String.valueOf(sellerId));
        fields.put("minBidUnit", String.valueOf(minBidUnit));
        fields.put("instantPrice", instantPrice != null ? String.valueOf(instantPrice) : "0");
        fields.put("endAt", endAt.toString());

        return redisTemplate.opsForHash().putAll(key, fields)
                .then(redisTemplate.expire(key, CACHE_TTL))
                .then()
                .doOnSuccess(v -> log.debug("Auction cache initialized: auctionId={}", auctionId));
    }

    @Override
    public Mono<Map<String, String>> getAuctionState(Long auctionId) {
        String key = auctionCurrentKey(auctionId);
        return redisTemplate.opsForHash().entries(key)
                .collectMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString()
                )
                .defaultIfEmpty(new HashMap<>());
    }

    @Override
    public Mono<Void> updateBidState(Long auctionId, Long currentPrice, Long winnerId, int bidCount) {
        String key = auctionCurrentKey(auctionId);
        Map<String, String> fields = new HashMap<>();
        fields.put("currentPrice", String.valueOf(currentPrice));
        fields.put("winnerId", String.valueOf(winnerId));
        fields.put("bidCount", String.valueOf(bidCount));

        return redisTemplate.opsForHash().putAll(key, fields).then();
    }

    @Override
    public Mono<Void> addBidToRanking(Long auctionId, Long bidderId, Long amount) {
        String key = auctionBidsKey(auctionId);
        return redisTemplate.opsForZSet()
                .add(key, String.valueOf(bidderId), amount.doubleValue())
                .then(redisTemplate.expire(key, CACHE_TTL))
                .then();
    }

    @Override
    public Mono<Void> scheduleEnding(Long auctionId, LocalDateTime endAt) {
        double score = endAt.toEpochSecond(ZoneOffset.UTC);
        return redisTemplate.opsForZSet()
                .add(ENDING_KEY, String.valueOf(auctionId), score)
                .then()
                .doOnSuccess(v -> log.debug("Auction ending scheduled: auctionId={}, endAt={}", auctionId, endAt));
    }

    @Override
    public Mono<Void> removeFromEnding(Long auctionId) {
        return redisTemplate.opsForZSet()
                .remove(ENDING_KEY, String.valueOf(auctionId))
                .then();
    }

    @Override
    public Mono<Void> cleanupAuctionCache(Long auctionId) {
        return redisTemplate.delete(auctionCurrentKey(auctionId))
                .then(redisTemplate.delete(auctionBidsKey(auctionId)))
                .then()
                .doOnSuccess(v -> log.debug("Auction cache cleaned up: auctionId={}", auctionId));
    }

    public Mono<java.util.Set<String>> getExpiredAuctionIds() {
        double now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        return redisTemplate.opsForZSet()
                .rangeByScore(ENDING_KEY, org.springframework.data.domain.Range.closed(0.0, now))
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Mono<Void> patchField(Long auctionId, String field, String value) {
        String key = auctionCurrentKey(auctionId);
        return redisTemplate.opsForHash().put(key, field, value).then();
    }

    private String auctionCurrentKey(Long auctionId) {
        return "auction:" + auctionId + ":current";
    }

    private String auctionBidsKey(Long auctionId) {
        return "auction:" + auctionId + ":bids";
    }
}
