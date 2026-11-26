package com.classpulse.common.protocol;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for length-prefixed TCP message framing.
 * <p>
 * Framing Specification:
 * <ul>
 *     <li>Header: 4 bytes, Big-Endian integer representing the length of the JSON payload.</li>
 *     <li>Payload: UTF-8 encoded JSON string of length matching the header.</li>
 *     <li>Maximum frame size: 64 KB (65,536 bytes) to prevent resource exhaustion.</li>
 * </ul>
 */
public final class ProtocolFramer {

    /** Maximum allowed frame payload size in bytes (64 KB). */
    public static final int MAX_FRAME_SIZE = 64 * 1024;

    /** Frame header length in bytes (4-byte big-endian integer). */
    public static final int HEADER_SIZE = 4;

    private ProtocolFramer() {
        // Utility class
    }

    /**
     * Encodes a JSON string into a length-prefixed byte array.
     *
     * @param json UTF-8 JSON string
     * @return 4-byte big-endian length prefix followed by UTF-8 payload bytes
     * @throws IllegalArgumentException if json is null or exceeds {@link #MAX_FRAME_SIZE}
     */
    public static byte[] encodeFrame(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON payload cannot be null");
        }
        byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
        if (payloadBytes.length > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("Payload size " + payloadBytes.length +
                    " bytes exceeds maximum allowed frame size of " + MAX_FRAME_SIZE + " bytes");
        }

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payloadBytes.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(payloadBytes.length);
        buffer.put(payloadBytes);
        return buffer.array();
    }

    /**
     * Encodes a {@link Message} into a length-prefixed byte array.
     *
     * @param message Message instance
     * @return encoded byte frame
     */
    public static byte[] encodeFrame(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        return encodeFrame(message.toJson());
    }

    /**
     * Decodes a single complete frame from a byte array.
     *
     * @param frameBytes byte array containing header and complete payload
     * @return decoded UTF-8 JSON string
     * @throws IllegalArgumentException if the frame is invalid, truncated, or exceeds {@link #MAX_FRAME_SIZE}
     */
    public static String decodeFrame(byte[] frameBytes) {
        if (frameBytes == null || frameBytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Frame bytes must be at least " + HEADER_SIZE + " bytes");
        }
        ByteBuffer buf = ByteBuffer.wrap(frameBytes).order(ByteOrder.BIG_ENDIAN);
        int length = buf.getInt();

        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("Invalid frame length: " + length +
                    " (max allowed is " + MAX_FRAME_SIZE + ")");
        }
        if (frameBytes.length != HEADER_SIZE + length) {
            throw new IllegalArgumentException("Frame size mismatch: expected " +
                    (HEADER_SIZE + length) + " bytes, but found " + frameBytes.length);
        }

        return new String(frameBytes, HEADER_SIZE, length, StandardCharsets.UTF_8);
    }

    /**
     * Decodes a single complete frame directly into a validated {@link Message}.
     */
    public static Message decodeMessage(byte[] frameBytes) {
        String json = decodeFrame(frameBytes);
        return Message.fromJson(json);
    }

    /**
     * Reads a single length-prefixed {@link Message} from a blocking input stream (e.g. TCP Socket).
     *
     * @param in InputStream to read from
     * @return decoded Message
     * @throws IOException if network error occurs, stream closes unexpectedly, or frame is invalid
     */
    public static Message readMessage(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        DataInputStream dis = (in instanceof DataInputStream) ? (DataInputStream) in : new DataInputStream(in);

        int length;
        try {
            length = dis.readInt();
        } catch (EOFException e) {
            return null; // Graceful connection close
        }

        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw new IOException("Invalid frame length received: " + length +
                    " (max allowed is " + MAX_FRAME_SIZE + ")");
        }

        byte[] payload = dis.readNBytes(length);
        if (payload.length < length) {
            throw new EOFException("Stream closed before complete frame was received (read " +
                    payload.length + " of " + length + " bytes)");
        }

        String json = new String(payload, StandardCharsets.UTF_8);
        return Message.fromJson(json);
    }

    /**
     * Writes a length-prefixed {@link Message} to an output stream and flushes.
     *
     * @param out     OutputStream to write to
     * @param message Message to send
     * @throws IOException if an I/O error occurs
     */
    public static void writeMessage(OutputStream out, Message message) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        byte[] frame = encodeFrame(message);
        out.write(frame);
        out.flush();
    }

    /**
     * Extracts complete {@link Message} objects from a Java NIO {@link ByteBuffer}.
     * Handles partial headers, partial payloads, and multiple concatenated frames safely.
     * Leaves incomplete frames in the buffer for future socket reads.
     *
     * @param buffer ByteBuffer in read mode (or flipped)
     * @return list of fully assembled Messages (empty if incomplete)
     * @throws IllegalArgumentException if an oversized frame length is encountered
     */
    public static List<Message> extractMessages(ByteBuffer buffer) {
        List<Message> messages = new ArrayList<>();
        if (buffer == null) {
            return messages;
        }

        buffer.order(ByteOrder.BIG_ENDIAN);

        while (buffer.remaining() >= HEADER_SIZE) {
            buffer.mark();
            int length = buffer.getInt();

            if (length < 0 || length > MAX_FRAME_SIZE) {
                throw new IllegalArgumentException("Invalid frame length: " + length +
                        " (max allowed is " + MAX_FRAME_SIZE + ")");
            }

            if (buffer.remaining() < length) {
                // Partial payload: reset buffer position back to before the header length
                buffer.reset();
                break;
            }

            byte[] payloadBytes = new byte[length];
            buffer.get(payloadBytes);
            String json = new String(payloadBytes, StandardCharsets.UTF_8);
            messages.add(Message.fromJson(json));
        }

        return messages;
    }
}
