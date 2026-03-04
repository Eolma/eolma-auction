package com.eolma.auction.adapter.out.redis;

import com.eolma.auction.application.port.out.DistributedLockPort;
import com.eolma.common.redis.DistributedLock;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class RedissonLockAdapter implements DistributedLockPort {

    private static final long WAIT_TIME_MS = 3000;
    private static final long LEASE_TIME_MS = 5000;

    private final DistributedLock distributedLock;

    public RedissonLockAdapter(DistributedLock distributedLock) {
        this.distributedLock = distributedLock;
    }

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> task) {
        return distributedLock.executeWithLock(lockKey, WAIT_TIME_MS, LEASE_TIME_MS, task);
    }
}
