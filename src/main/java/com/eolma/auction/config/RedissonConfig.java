package com.eolma.auction.config;

import com.eolma.common.idempotency.IdempotencyChecker;
import com.eolma.common.idempotency.ProcessedEventRepository;
import com.eolma.common.redis.DistributedLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public DistributedLock distributedLock(RedissonClient redissonClient) {
        return new DistributedLock(redissonClient);
    }

    @Bean
    public IdempotencyChecker idempotencyChecker(ProcessedEventRepository processedEventRepository) {
        return new IdempotencyChecker(processedEventRepository);
    }
}
