package com.classpulse.server.udp;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe tracker that records client UDP heartbeats and evaluates
 * {@link ClientPresenceStatus} based on a configured timeout.
 */
public class ClientStatusTracker {

    private final Duration timeout;
    private final Clock clock;
    private final ConcurrentHashMap<String, ClientHeartbeatRecord> records = new ConcurrentHashMap<>();

    public ClientStatusTracker(Duration timeout) {
        this(timeout, Clock.systemUTC());
    }

    public ClientStatusTracker(Duration timeout, Clock clock) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive: " + timeout);
        }
        this.timeout = timeout;
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    /**
     * Records a heartbeat for the given client identifier using the tracker clock.
     *
     * @param clientId      non-blank client or session identifier
     * @param role          optional client role (e.g. STUDENT, LECTURER)
     * @param classroomCode optional classroom code
     * @param remoteAddress optional sender UDP socket address
     * @return updated heartbeat record
     */
    public ClientHeartbeatRecord recordHeartbeat(
            String clientId,
            String role,
            String classroomCode,
            InetSocketAddress remoteAddress) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
        String normalizedId = clientId.trim();
        String normalizedRole = trimToNull(role);
        String normalizedRoom = trimToNull(classroomCode);
        Instant now = clock.instant();

        return records.compute(normalizedId, (id, existing) -> {
            long count = (existing == null) ? 1L : existing.getHeartbeatCount() + 1L;
            return new ClientHeartbeatRecord(
                    id,
                    normalizedRole,
                    normalizedRoom,
                    remoteAddress,
                    now,
                    count);
        });
    }

    /**
     * Evaluates the current presence status of a client.
     *
     * @param clientId client identifier to look up
     * @return {@link ClientPresenceStatus#UNKNOWN} if never seen or invalid ID,
     * {@link ClientPresenceStatus#ONLINE} if within timeout, otherwise {@link ClientPresenceStatus#OFFLINE}
     */
    public ClientPresenceStatus getStatus(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return ClientPresenceStatus.UNKNOWN;
        }
        ClientHeartbeatRecord record = records.get(clientId.trim());
        if (record == null) {
            return ClientPresenceStatus.UNKNOWN;
        }
        return evaluateStatus(record, clock.instant());
    }

    /**
     * Returns the most recent heartbeat record for the given client, if known.
     */
    public Optional<ClientHeartbeatRecord> getRecord(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(clientId.trim()));
    }

    /**
     * Returns an unmodifiable snapshot of current statuses for all seen clients.
     */
    public Map<String, ClientPresenceStatus> getAllStatuses() {
        Instant now = clock.instant();
        Map<String, ClientPresenceStatus> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, ClientHeartbeatRecord> entry : records.entrySet()) {
            snapshot.put(entry.getKey(), evaluateStatus(entry.getValue(), now));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Returns an unmodifiable snapshot of all recorded client heartbeat entries.
     */
    public Map<String, ClientHeartbeatRecord> getAllRecords() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(records));
    }

    public Duration getTimeout() {
        return timeout;
    }

    private ClientPresenceStatus evaluateStatus(ClientHeartbeatRecord record, Instant now) {
        Duration elapsed = Duration.between(record.getLastSeen(), now);
        return elapsed.compareTo(timeout) <= 0
                ? ClientPresenceStatus.ONLINE
                : ClientPresenceStatus.OFFLINE;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
