package com.medicaldrone.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;

/**
 * Distributed Lock Service backed by Redis.
 * Satisfies Section 26: Inventory reservation locks to prevent simultaneous acceptance from exceeding stock.
 * Features an automatic in-memory fallback lock mechanism when Redis is offline in standalone environments.
 */
@Service
public class RedisLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, ReentrantLock> localLockMap = new ConcurrentHashMap<>();
    private final ThreadLocal<Map<String, ReentrantLock>> localLocksHeld = ThreadLocal.withInitial(java.util.HashMap::new);

    public RedisLockService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Acquire a distributed lock.
     *
     * @param lockKey Key name (e.g., "lock:inventory:hospital:1:item:5")
     * @param lockValue Unique token / holder identifier
     * @param timeoutSeconds Duration after which lock automatically expires
     * @return true if lock was acquired
     */
    public boolean acquireLock(String lockKey, String lockValue, long timeoutSeconds) {
        try {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(timeoutSeconds));
            if (Boolean.TRUE.equals(success)) {
                log.debug("Redis distributed lock acquired for {}", lockKey);
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.warn("Redis unavailable for lock {}. Using in-memory JVM reentrant lock fallback.", lockKey);
            ReentrantLock localLock = localLockMap.computeIfAbsent(lockKey, k -> new ReentrantLock());
            try {
                boolean acquired = localLock.tryLock(timeoutSeconds, TimeUnit.SECONDS);
                if (acquired) localLocksHeld.get().put(lockKey, localLock);
                return acquired;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * Release a previously acquired lock safely.
     *
     * @param lockKey Key name
     * @param lockValue Unique token that originally acquired the lock
     */
    public void releaseLock(String lockKey, String lockValue) {
        ReentrantLock localLock = localLocksHeld.get().remove(lockKey);
        if (localLock != null) {
            if (localLock.isHeldByCurrentThread()) localLock.unlock();
            if (localLocksHeld.get().isEmpty()) localLocksHeld.remove();
            return;
        }
        try {
            String currentValue = stringRedisTemplate.opsForValue().get(lockKey);
            if (lockValue != null && lockValue.equals(currentValue)) {
                stringRedisTemplate.delete(lockKey);
                log.debug("Redis distributed lock released for {}", lockKey);
            }
        } catch (Exception ex) {
            log.debug("Redis lock release deferred to expiry for {}", lockKey);
        }
    }
}
