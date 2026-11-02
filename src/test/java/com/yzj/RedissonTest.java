package com.yzj;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedissonTest {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    void shouldAcquireAndReleaseLock() throws Exception {
        RLock lock = redissonClient.getLock("test:redisson:lock");

        // 只传等待时间 -> 看门狗生效
        boolean locked = lock.tryLock(3, TimeUnit.SECONDS);
        assertTrue(locked, "应该能拿到锁");

        try {
            assertTrue(lock.isLocked(), "锁应该处于锁定状态");
            assertTrue(lock.isHeldByCurrentThread(), "锁应该由当前线程持有");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        assertFalse(lock.isLocked(), "释放后不应该再锁着");
    }
}