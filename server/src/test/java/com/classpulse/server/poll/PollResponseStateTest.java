package com.classpulse.server.poll;

import com.classpulse.common.model.Poll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PollResponseStateTest {

    @Test
    @DisplayName("Concurrent submissions from unique students are counted accurately without race conditions")
    void testConcurrentUniqueSubmissions() throws Exception {
        Poll poll = new Poll("poll-1", "CS101", "Select an option", Arrays.asList("A", "B", "C", "D"));
        PollResponseState state = new PollResponseState(poll);

        int studentCount = 120;
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(studentCount);
        AtomicInteger accepted = new AtomicInteger();

        for (int i = 0; i < studentCount; i++) {
            final String studentId = "student-" + i;
            final int option = i % 4;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    state.recordAnswer(studentId, option);
                    accepted.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        assertEquals(studentCount, accepted.get());
        PollResult result = state.snapshotResult();
        assertEquals(studentCount, result.getTotalResponses());
        assertEquals(30, result.getVotesForOption(0));
        assertEquals(30, result.getVotesForOption(1));
        assertEquals(30, result.getVotesForOption(2));
        assertEquals(30, result.getVotesForOption(3));
    }

    @Test
    @DisplayName("Concurrent duplicate submissions from the same student allow only one answer")
    void testConcurrentDuplicateSubmissions() throws Exception {
        Poll poll = new Poll("poll-dup", "CS101", "Pick one", Arrays.asList("Yes", "No"));
        PollResponseState state = new PollResponseState(poll);

        int attempts = 20;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(attempts);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            final int option = i % 2;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    state.recordAnswer("same-student", option);
                    accepted.incrementAndGet();
                } catch (IllegalStateException e) {
                    duplicates.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        assertEquals(1, accepted.get());
        assertEquals(attempts - 1, duplicates.get());
        assertEquals(1, state.snapshotResult().getTotalResponses());
    }

    @Test
    @DisplayName("Concurrent readers always see a consistent snapshot while writers submit")
    void testConsistentSnapshotDuringConcurrentWrites() throws Exception {
        Poll poll = new Poll("poll-snap", "CS101", "Choose", Arrays.asList("Opt0", "Opt1", "Opt2"));
        PollResponseState state = new PollResponseState(poll);

        int writers = 90;
        int readers = 30;
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(writers + readers);
        AtomicReference<AssertionError> failure = new AtomicReference<>();

        for (int i = 0; i < writers; i++) {
            final String studentId = "w-" + i;
            final int option = i % 3;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    state.recordAnswer(studentId, option);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (int i = 0; i < readers; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int r = 0; r < 10; r++) {
                        PollResult snapshot = state.snapshotResult();
                        int sum = snapshot.getVotesForOption(0)
                                + snapshot.getVotesForOption(1)
                                + snapshot.getVotesForOption(2);
                        if (sum != snapshot.getTotalResponses()) {
                            failure.compareAndSet(null, new AssertionError(
                                    "Inconsistent snapshot: total=" + snapshot.getTotalResponses()
                                            + ", sum=" + sum));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        assertNull(failure.get());
        assertEquals(writers, state.snapshotResult().getTotalResponses());
    }
}
