package com.classpulse.server.multicast;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Immutable representation of a classroom multicast announcement.
 */
public final class MulticastAnnouncement {

    private final String requestId;
    private final String classroomCode;
    private final String sender;
    private final String message;
    private final long timestamp;
    private final InetSocketAddress sourceAddress;

    public MulticastAnnouncement(
            String requestId,
            String classroomCode,
            String sender,
            String message,
            long timestamp,
            InetSocketAddress sourceAddress) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("requestId must not be null or blank");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Announcement message must not be null or blank");
        }
        this.requestId = requestId.trim();
        this.classroomCode = trimToNull(classroomCode);
        this.sender = trimToNull(sender);
        this.message = message.trim();
        this.timestamp = timestamp;
        this.sourceAddress = sourceAddress;
    }

    /**
     * Converts this announcement into a protocol {@link Message} of type {@link MessageType#ANNOUNCEMENT}.
     */
    public Message toProtocolMessage() {
        Message protocolMessage = new Message(MessageType.ANNOUNCEMENT, requestId);
        protocolMessage.putPayload("message", message);
        protocolMessage.putPayload("timestamp", timestamp);
        if (classroomCode != null) {
            protocolMessage.putPayload("classroomCode", classroomCode);
        }
        if (sender != null) {
            protocolMessage.putPayload("sender", sender);
        }
        return protocolMessage;
    }

    /**
     * Parses a protocol {@link MessageType#ANNOUNCEMENT} message into a {@link MulticastAnnouncement}.
     */
    public static MulticastAnnouncement fromProtocolMessage(Message msg, InetSocketAddress source) {
        Objects.requireNonNull(msg, "Message must not be null");
        if (msg.getType() != MessageType.ANNOUNCEMENT) {
            throw new IllegalArgumentException("Expected ANNOUNCEMENT message, got " + msg.getType());
        }
        String text = msg.getPayloadString("message");
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("ANNOUNCEMENT payload must contain a non-blank 'message'");
        }
        Object tsField = msg.getPayloadField("timestamp");
        long ts = (tsField instanceof Number number) ? number.longValue() : System.currentTimeMillis();
        return new MulticastAnnouncement(
                msg.getRequestId(),
                msg.getPayloadString("classroomCode"),
                msg.getPayloadString("sender"),
                text,
                ts,
                source);
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
