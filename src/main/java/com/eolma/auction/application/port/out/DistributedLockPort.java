package com.eolma.auction.application.port.out;

import java.util.function.Supplier;

public interface DistributedLockPort {

    <T> T executeWithLock(String lockKey, Supplier<T> task);
}
