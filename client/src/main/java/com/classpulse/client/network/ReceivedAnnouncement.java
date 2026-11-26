package com.classpulse.client.network;

import java.net.InetSocketAddress;

/**
 * Immutable representation of an announcement received over UDP multicast.
 */
public final class ReceivedAnnouncement {

    private final String requestId;
    private final String classroomCode;
    private final String sender;
    private final String message;
    private final long timestamp;
    private final InetSocketAddress sourceAddress;

    public ReceivedAnnouncement(
            String requestId,
            String classroomCode,
            String sender,
            String message,
            long timestamp,
            InetSocketAddress sourceAddress) {
        this.requestId = requestId;
        this.classroomCode = classroomCode;
        this.sender = sender;
        this.message = message;
        this.timestamp = timestamp;
        this.sourceAddress = sourceAddress;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getClassroomCode() {
        return classroomCode;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public InetSocketAddress getSourceAddress() {
        return sourceAddress;
    }

    @Override
    public String toString() {
        return "ReceivedAnnouncement{" +
                "requestId='" + requestId + '\'' +
                ", classroomCode='" + classroomCode + '\'' +
                ", sender='" + sender + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", sourceAddress=" + sourceAddress +
                '}';
    }
}
