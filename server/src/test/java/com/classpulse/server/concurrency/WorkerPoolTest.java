package com.classpulse.server.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkerPoolTest {

    @Test
    @DisplayName("Constructor rejects invalid pool size, queue capacity, or timeout")
    void testInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerPool(0));
        assertThrows(IllegalArgumentException.class, () -> new WorkerPool(2, 0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new WorkerPool(2, 10, -1));
    }

    @Test
    @DisplayName("WorkerPool runs tasks concurrently on bounded named worker threads")
    void testConcurrentExecutionAndBoundedPoolSize() throws Exception {
        int poolSize = 4;
        try (WorkerPool pool = new WorkerPool(poolSize, 64, 2000)) {
            assertEquals(poolSize, pool.getPoolSize());

            int taskCount = 12;
            CountDownLatch allStarted = new CountDownLatch(poolSize);
            CountDownLatch releaseWorkers = new CountDownLatch(1);
            CountDownLatch allDone = new CountDownLatch(taskCount);
            Set<String> threadNames = ConcurrentHashMap.newKeySet();

            for (int i = 0; i < taskCount; i++) {
                pool.execute(() -> {
                    threadNames.add(Thread.currentThread().getName());
                    allStarted.countDown();
                    try {
                        releaseWorkers.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        allDone.countDown();
                    }
                });
            }

            assertTrue(allStarted.await(2, TimeUnit.SECONDS), "All worker threads should run concurrently");
            releaseWorkers.countDown();
            assertTrue(allDone.await(3, TimeUnit.SECONDS), "All queued tasks should complete");

            assertTrue(threadNames.size() <= poolSize);
            assertTrue(threadNames.stream().allMatch(name -> name.startsWith("classpulse-worker-")));
        }
    }

    @Test
    @DisplayName("WorkerPool rejects tasks when bounded queue is full")
    void testBoundedQueueRejection() throws Exception {
        try (WorkerPool pool = new WorkerPool(1, 1, 2000)) {
            CountDownLatch blocker = new CountDownLatch(1);
            CountDownLatch firstStarted = new CountDownLatch(1);

            // Occupy the single worker thread
            pool.execute(() -> {
                firstStarted.countDown();
                try {
                    blocker.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            // Fill the single queue slot
            pool.execute(() -> {});

            // Third task must be rejected immediately
            assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {}));
            blocker.countDown();
        }
    }

    @Test
    @DisplayName("WorkerPool shutdown waits for in-flight tasks and terminates cleanly")
    void testCleanShutdown() throws Exception {
        WorkerPool pool = new WorkerPool(2, 16, 2000);
        AtomicInteger completed = new AtomicInteger(0);
        CountDownLatch started = new CountDownLatch(2);

        for (int i = 0; i < 4; i++) {
            pool.execute(() -> {
                started.countDown();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                completed.incrementAndGet();
            });
        }

        assertTrue(started.await(2, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(pool.isShutdown());
        assertTrue(pool.isTerminated());
        assertEquals(4, completed.get());
        assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {}));
    }
}
