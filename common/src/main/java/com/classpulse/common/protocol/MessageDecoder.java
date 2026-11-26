package com.classpulse.common.protocol;

/**
 * Decodes JSON strings into {@link Message} objects.
 */
public final class MessageDecoder {

    private MessageDecoder() {
        // Utility class
    }

    /**
     * Deserializes a JSON string into a validated {@link Message}.
     *
     * @param json JSON string representing a message envelope
     * @return decoded {@link Message}
     * @throws IllegalArgumentException if {@code json} is null, blank, malformed, or missing required fields
     */
    public static Message decode(String json) {
        return Message.fromJson(json);
    }
}
