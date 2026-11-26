package com.classpulse.server.udp;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of the most recent UDP heartbeat received from a client.
 */
public final class ClientHeartbeatRecord {

    private final String clientId;
    private final String role;
    private final String classroomCode;
    private final InetSocketAddress remoteAddress;
    private final Instant lastSeen;
    private final long heartbeatCount;

    public ClientHeartbeatRecord(
            String clientId,
            String role,
            String classroomCode,
            InetSocketAddress remoteAddress,
            Instant lastSeen,
            long heartbeatCount) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.role = role;
        this.classroomCode = classroomCode;
        this.remoteAddress = remoteAddress;
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen must not be null");
        this.heartbeatCount = heartbeatCount;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRole() {
        return role;
    }

    public String getClassroomCode() {
        return classroomCode;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public long getHeartbeatCount() {
        return heartbeatCount;
    }

    @Override
    public String toString() {
        return "ClientHeartbeatRecord{" +
                "clientId='" + clientId + '\'' +
                ", role='" + role + '\'' +
                ", classroomCode='" + classroomCode + '\'' +
                ", remoteAddress=" + remoteAddress +
                ", lastSeen=" + lastSeen +
                ", heartbeatCount=" + heartbeatCount +
                '}';
    }
}
