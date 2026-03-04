package com.eolma.auction.adapter.out.persistence;

import com.eolma.common.idempotency.ProcessedEventRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ProcessedEventR2dbcRepository implements ProcessedEventRepository {

    private static final String KEY_PREFIX = "processed_event:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    public ProcessedEventR2dbcRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + eventId));
    }

    @Override
    public void save(String eventId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + eventId, "1", TTL);
    }
}
