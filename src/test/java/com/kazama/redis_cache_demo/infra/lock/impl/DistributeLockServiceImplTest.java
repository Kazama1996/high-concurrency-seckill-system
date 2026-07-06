package com.kazama.redis_cache_demo.infra.lock.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class DistributeLockServiceImplTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;

    @InjectMocks private DistributeLockServiceImpl lockService;

    private static final String LOCK_KEY = "test:lock:key";

    @AfterEach
    void clearInterruptFlag() {
        // safety net in case a test doesn't reach its own Thread.interrupted() assertion
        Thread.interrupted();
    }

    @Test
    void executeWithLock_acquired_runsSupplierAndUnlocks() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = lockService.executeWithLock(LOCK_KEY, 3L, 10L, TimeUnit.SECONDS, () -> "done");

        assertEquals("done", result);
        verify(lock).unlock();
    }

    @Test
    void executeWithLock_acquired_notHeldByCurrentThread_doesNotUnlock() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        lockService.executeWithLock(LOCK_KEY, 3L, 10L, TimeUnit.SECONDS, () -> "done");

        verify(lock, never()).unlock();
    }

    @Test
    void executeWithLock_notAcquired_throwsAndNeverRunsSupplier() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(false);
        AtomicBoolean supplierRan = new AtomicBoolean(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                lockService.executeWithLock(LOCK_KEY, 3L, 10L, TimeUnit.SECONDS, () -> {
                    supplierRan.set(true);
                    return "done";
                }));

        assertEquals("Can not acquire lock" + LOCK_KEY, ex.getMessage());
        assertFalse(supplierRan.get());
    }

    @Test
    void executeWithLock_interrupted_restoresInterruptFlagAndWrapsException() throws InterruptedException {
        InterruptedException cause = new InterruptedException("boom");
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenThrow(cause);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                lockService.executeWithLock(LOCK_KEY, 3L, 10L, TimeUnit.SECONDS, () -> "done"));

        assertEquals("Lock acquire process is interrupted", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertTrue(Thread.interrupted()); // checks AND clears the flag
    }

    @Test
    void executeWithLock_supplierThrows_stillUnlocksAndPropagates() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RuntimeException boom = new RuntimeException("supplier failure");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                lockService.executeWithLock(LOCK_KEY, 3L, 10L, TimeUnit.SECONDS, () -> {
                    throw boom;
                }));

        assertSame(boom, ex);
        verify(lock).unlock();
    }

    @Test
    void executeWithLock_runnableOverload_delegatesAndRuns() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        AtomicBoolean ran = new AtomicBoolean(false);

        lockService.executeWithLock(LOCK_KEY, 3L, 10L, TimeUnit.SECONDS, () -> ran.set(true));

        assertTrue(ran.get());
        verify(lock).unlock();
    }
}
