package com.classpulse.server.concurrency;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded {@link ThreadPoolExecutor}-based worker pool for offloading application
 * request processing from the single-threaded NIO selector event loop.
 */
public class WorkerPool implements AutoCloseable {

    public static final int DEFAULT_POOL_SIZE =
            Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));
    public static final int DEFAULT_QUEUE_CAPACITY = 1024;
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 5000L;

    private final ThreadPoolExecutor executor;
    private final long shutdownTimeoutMillis;

    public WorkerPool() {
        this(DEFAULT_POOL_SIZE, DEFAULT_QUEUE_CAPACITY, DEFAULT_SHUTDOWN_TIMEOUT_MILLIS);
    }

    public WorkerPool(int poolSize) {
        this(poolSize, DEFAULT_QUEUE_CAPACITY, DEFAULT_SHUTDOWN_TIMEOUT_MILLIS);
    }

    /**
     * Creates a fixed-size worker pool backed by a bounded task queue.
     *
     * @param poolSize              number of worker threads (must be &gt;= 1)
     * @param queueCapacity         maximum queued tasks before rejecting (must be &gt;= 1)
     * @param shutdownTimeoutMillis graceful shutdown wait timeout in milliseconds (&gt;= 0)
     */
    public WorkerPool(int poolSize, int queueCapacity, long shutdownTimeoutMillis) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be at least 1: " + poolSize);
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be at least 1: " + queueCapacity);
        }
        if (shutdownTimeoutMillis < 0) {
            throw new IllegalArgumentException("shutdownTimeoutMillis must not be negative: " + shutdownTimeoutMillis);
        }

        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);
        ThreadFactory threadFactory = new NamedWorkerThreadFactory("classpulse-worker-");
        this.executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                workQueue,
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * Submits a task for asynchronous execution by a worker thread.
     *
     * @param task runnable task to execute
     * @throws RejectedExecutionException if the pool is shut down or its queue is full
     */
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        executor.execute(task);
    }

    /**
     * Submits a task and returns a {@link Future} representing its pending completion.
     *
     * @param task runnable task to execute
     * @return future for the submitted task
     * @throws RejectedExecutionException if the pool is shut down or its queue is full
     */
    public Future<?> submit(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        return executor.submit(task);
    }

    /**
     * Initiates an orderly shutdown, waits up to the configured timeout for running
     * tasks to finish, and forces termination if tasks do not complete in time.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(1000L, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public int getPoolSize() {
        return executor.getCorePoolSize();
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }

    private static final class NamedWorkerThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedWorkerThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
