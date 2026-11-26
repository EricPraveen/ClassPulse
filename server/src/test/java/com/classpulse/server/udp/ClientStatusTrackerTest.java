package com.classpulse.server.udp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClientStatusTrackerTest {

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advanceBy(Duration duration) {
            current.updateAndGet(t -> t.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    @Test
    @DisplayName("Unknown, null, and blank client IDs return UNKNOWN status and empty record")
    void testUnknownClientReturnsUnknown() {
        ClientStatusTracker tracker = new ClientStatusTracker(Duration.ofSeconds(15));

        assertEquals(ClientPresenceStatus.UNKNOWN, tracker.getStatus(null));
        assertEquals(ClientPresenceStatus.UNKNOWN, tracker.getStatus("   "));
        assertEquals(ClientPresenceStatus.UNKNOWN, tracker.getStatus("student-unknown"));
        assertEquals(Optional.empty(), tracker.getRecord(null));
        assertEquals(Optional.empty(), tracker.getRecord("  "));
        assertEquals(Optional.empty(), tracker.getRecord("student-unknown"));
    }

    @Test
    @DisplayName("Recording heartbeat sets ONLINE, transitions to OFFLINE after timeout, and recovers on new heartbeat")
    void testOnlineToOfflineTransitionAndRecovery() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-27T00:00:00Z"));
        ClientStatusTracker tracker = new ClientStatusTracker(Duration.ofSeconds(15), clock);

        ClientHeartbeatRecord first = tracker.recordHeartbeat(
                " student-1 ",
                "STUDENT",
                "CS101",
                new InetSocketAddress("127.0.0.1", 54321));

        assertEquals("student-1", first.getClientId());
        assertEquals("STUDENT", first.getRole());
        assertEquals("CS101", first.getClassroomCode());
        assertEquals(1L, first.getHeartbeatCount());
        assertEquals(ClientPresenceStatus.ONLINE, tracker.getStatus("student-1"));

        clock.advanceBy(Duration.ofSeconds(15));
        assertEquals(ClientPresenceStatus.ONLINE, tracker.getStatus("student-1"));

        clock.advanceBy(Duration.ofMillis(1));
        assertEquals(ClientPresenceStatus.OFFLINE, tracker.getStatus("student-1"));
        assertEquals(ClientPresenceStatus.OFFLINE, tracker.getAllStatuses().get("student-1"));

        ClientHeartbeatRecord second = tracker.recordHeartbeat(
                "student-1",
                "STUDENT",
                "CS101",
                new InetSocketAddress("127.0.0.1", 54321));
        assertEquals(2L, second.getHeartbeatCount());
        assertEquals(ClientPresenceStatus.ONLINE, tracker.getStatus("student-1"));
    }

    @Test
    @DisplayName("Rejects invalid timeout and blank clientId")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ClientStatusTracker(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ClientStatusTracker(Duration.ofSeconds(-1)));

        ClientStatusTracker tracker = new ClientStatusTracker(Duration.ofSeconds(10));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.recordHeartbeat("   ", "STUDENT", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.recordHeartbeat(null, "STUDENT", null, null));
    }
}
