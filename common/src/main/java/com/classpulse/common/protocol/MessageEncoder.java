package com.classpulse.common.protocol;

/**
 * Encodes {@link Message} objects into JSON strings.
 */
public final class MessageEncoder {

    private MessageEncoder() {
        // Utility class
    }

    /**
     * Serializes a {@link Message} into its JSON representation.
     *
     * @param message the message to serialize
     * @return JSON string
     * @throws IllegalArgumentException if {@code message} is null or invalid
     */
    public static String encode(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        return message.toJson();
    }
}
