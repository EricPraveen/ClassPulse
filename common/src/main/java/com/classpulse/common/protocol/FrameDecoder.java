package com.classpulse.common.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stateful TCP stream frame decoder.
 * <p>
 * Accumulates incoming {@link ByteBuffer} chunks and extracts complete
 * length-prefixed {@link Message} frames ({@code [4-byte big-endian length][UTF-8 JSON bytes]}).
 * Incomplete headers or payloads are preserved across calls until enough bytes arrive.
 */
public class FrameDecoder {

    private final int maxFrameSize;
    private byte[] buffer;
    private int bufferedLength;

    /**
     * Creates a {@link FrameDecoder} using {@link ProtocolFramer#MAX_FRAME_SIZE}.
     */
    public FrameDecoder() {
        this(ProtocolFramer.MAX_FRAME_SIZE);
    }

    /**
     * Creates a {@link FrameDecoder} with a custom maximum frame payload size.
     *
     * @param maxFrameSize maximum allowed payload size in bytes (must be positive)
     */
    public FrameDecoder(int maxFrameSize) {
        if (maxFrameSize <= 0) {
            throw new IllegalArgumentException("maxFrameSize must be positive: " + maxFrameSize);
        }
        this.maxFrameSize = maxFrameSize;
        this.buffer = new byte[1024];
        this.bufferedLength = 0;
    }

    /**
     * Appends incoming bytes and decodes all complete messages currently available.
     * Any trailing incomplete frame data is retained for subsequent calls.
     *
     * @param incoming incoming {@link ByteBuffer} (read from its current position to limit)
     * @return list of decoded {@link Message} objects (empty if no complete frame is available yet)
     * @throws IllegalArgumentException if a frame length is invalid/oversized or the JSON payload is malformed
     */
    public List<Message> decode(ByteBuffer incoming) {
        List<Message> messages = new ArrayList<>();
        if (incoming == null || !incoming.hasRemaining()) {
            return messages;
        }

        appendIncoming(incoming);

        int offset = 0;
        while (bufferedLength - offset >= ProtocolFramer.HEADER_SIZE) {
            int length = ByteBuffer.wrap(buffer, offset, ProtocolFramer.HEADER_SIZE)
                    .order(ByteOrder.BIG_ENDIAN)
                    .getInt();

            if (length <= 0 || length > maxFrameSize) {
                reset();
                throw new IllegalArgumentException("Invalid frame length: " + length +
                        " (allowed: 1.." + maxFrameSize + ")");
            }

            if (bufferedLength - offset < ProtocolFramer.HEADER_SIZE + length) {
                break; // Wait for more data
            }

            String json = new String(buffer, offset + ProtocolFramer.HEADER_SIZE, length, StandardCharsets.UTF_8);
            offset += ProtocolFramer.HEADER_SIZE + length;
            messages.add(MessageDecoder.decode(json));
        }

        compact(offset);
        return messages;
    }

    /**
     * Returns the number of unread bytes currently buffered.
     */
    public int bufferedBytes() {
        return bufferedLength;
    }

    /**
     * Clears any buffered incomplete frame data.
     */
    public void reset() {
        bufferedLength = 0;
    }

    private void appendIncoming(ByteBuffer incoming) {
        int incomingBytes = incoming.remaining();
        ensureCapacity(bufferedLength + incomingBytes);
        incoming.get(buffer, bufferedLength, incomingBytes);
        bufferedLength += incomingBytes;
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= buffer.length) {
            return;
        }
        int newCapacity = Math.max(buffer.length * 2, requiredCapacity);
        buffer = Arrays.copyOf(buffer, newCapacity);
    }

    private void compact(int consumed) {
        if (consumed == 0) {
            return;
        }
        int remaining = bufferedLength - consumed;
        if (remaining > 0) {
            System.arraycopy(buffer, consumed, buffer, 0, remaining);
        }
        bufferedLength = remaining;
    }
}
