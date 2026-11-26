package com.classpulse.common.protocol;

/**
 * Minimal shared message envelope.
 *
 * The payload is deliberately a String placeholder until the team agrees
 * on a serialization format and TCP framing strategy.
 */
public class Message {
    private MessageType type;
    private String payload;

    public Message() {
    }

    public Message(MessageType type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
