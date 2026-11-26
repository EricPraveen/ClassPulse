package com.classpulse.common.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Encodes {@link Message} objects or JSON strings into length-prefixed TCP frames.
 * <p>
 * Frame format: {@code [4-byte big-endian length][UTF-8 JSON bytes]}.
 */
public final class FrameEncoder {

    private FrameEncoder() {
        // Utility class
    }

    /**
     * Encodes a JSON string into a length-prefixed {@link ByteBuffer} ready for reading.
     *
     * @param json UTF-8 JSON string
     * @return {@link ByteBuffer} in big-endian order, flipped and ready to read
     * @throws IllegalArgumentException if {@code json} is null or exceeds {@link ProtocolFramer#MAX_FRAME_SIZE}
     */
    public static ByteBuffer encode(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON payload cannot be null");
        }
        byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
        if (payloadBytes.length > ProtocolFramer.MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("Payload size " + payloadBytes.length +
                    " bytes exceeds maximum allowed frame size of " + ProtocolFramer.MAX_FRAME_SIZE + " bytes");
        }

        ByteBuffer buffer = ByteBuffer.allocate(ProtocolFramer.HEADER_SIZE + payloadBytes.length)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(payloadBytes.length);
        buffer.put(payloadBytes);
        buffer.flip();
        return buffer;
    }

    /**
     * Encodes a {@link Message} into a length-prefixed {@link ByteBuffer} ready for reading.
     *
     * @param message the message to encode
     * @return {@link ByteBuffer} in big-endian order, flipped and ready to read
     * @throws IllegalArgumentException if {@code message} is null, invalid, or exceeds {@link ProtocolFramer#MAX_FRAME_SIZE}
     */
    public static ByteBuffer encode(Message message) {
        return encode(MessageEncoder.encode(message));
    }
}
