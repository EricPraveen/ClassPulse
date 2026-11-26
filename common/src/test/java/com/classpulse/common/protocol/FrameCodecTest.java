package com.classpulse.common.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrameCodecTest {

    @Test
    @DisplayName("MessageEncoder should serialize a Message into JSON")
    void testMessageEncoder() {
        Message msg = new Message(MessageType.CREATE_CLASSROOM, "req-101");
        msg.putPayload("lecturerName", "Prof. Ada");

        String json = MessageEncoder.encode(msg);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"CREATE_CLASSROOM\""));
        assertTrue(json.contains("\"requestId\":\"req-101\""));
        assertTrue(json.contains("\"lecturerName\":\"Prof. Ada\""));

        assertThrows(IllegalArgumentException.class, () -> MessageEncoder.encode(null));
    }

    @Test
    @DisplayName("MessageDecoder should deserialize JSON back into a Message")
    void testMessageDecoder() {
        Message original = new Message(MessageType.JOIN_CLASSROOM, "req-102");
        original.putPayload("classroomCode", "CS101");
        original.putPayload("studentName", "Bob");

        String json = MessageEncoder.encode(original);
        Message decoded = MessageDecoder.decode(json);

        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("FrameEncoder and FrameDecoder should encode and decode a complete frame")
    void testCompleteFrameEncodeDecode() {
        Message original = new Message(MessageType.SUBMIT_ANSWER, "req-103");
        original.putPayload("pollId", "poll-1");
        original.putPayload("selectedOption", 2);

        ByteBuffer encoded = FrameEncoder.encode(original);
        assertEquals(ByteOrder.BIG_ENDIAN, encoded.order());

        int payloadLength = encoded.getInt(0);
        assertEquals(encoded.remaining() - ProtocolFramer.HEADER_SIZE, payloadLength);

        FrameDecoder decoder = new FrameDecoder();
        List<Message> decoded = decoder.decode(encoded);

        assertEquals(1, decoded.size());
        assertEquals(original, decoded.get(0));
        assertEquals(0, decoder.bufferedBytes());
    }

    @Test
    @DisplayName("FrameDecoder should decode a frame split across multiple buffers")
    void testSplitFrameAcrossMultipleBuffers() {
        Message original = new Message(MessageType.CREATE_POLL, "req-104");
        original.putPayload("classroomCode", "NET101");
        original.putPayload("question", "What is TCP?");
        original.putPayload("options", List.of("Stream", "Datagram"));

        ByteBuffer fullFrame = FrameEncoder.encode(original);
        byte[] allBytes = new byte[fullFrame.remaining()];
        fullFrame.get(allBytes);

        FrameDecoder decoder = new FrameDecoder();

        // Chunk 1: partial header (2 bytes)
        assertTrue(decoder.decode(ByteBuffer.wrap(allBytes, 0, 2)).isEmpty());
        assertEquals(2, decoder.bufferedBytes());

        // Chunk 2: rest of header + partial payload
        int mid = 10;
        assertTrue(decoder.decode(ByteBuffer.wrap(allBytes, 2, mid - 2)).isEmpty());
        assertEquals(mid, decoder.bufferedBytes());

        // Chunk 3: remaining payload
        List<Message> decoded = decoder.decode(ByteBuffer.wrap(allBytes, mid, allBytes.length - mid));
        assertEquals(1, decoded.size());
        assertEquals(original, decoded.get(0));
        assertEquals(0, decoder.bufferedBytes());
    }

    @Test
    @DisplayName("FrameDecoder should decode multiple frames from a single buffer")
    void testMultipleFramesInOneBuffer() {
        Message m1 = new Message(MessageType.HEARTBEAT, "hb-1");
        Message m2 = new Message(MessageType.SUCCESS, "req-200").putPayload("message", "OK");
        Message m3 = new Message(MessageType.DISCONNECT, "req-201").putPayload("reason", "Done");

        ByteBuffer b1 = FrameEncoder.encode(m1);
        ByteBuffer b2 = FrameEncoder.encode(m2);
        ByteBuffer b3 = FrameEncoder.encode(m3);

        ByteBuffer combined = ByteBuffer.allocate(b1.remaining() + b2.remaining() + b3.remaining());
        combined.put(b1).put(b2).put(b3);
        combined.flip();

        FrameDecoder decoder = new FrameDecoder();
        List<Message> decoded = decoder.decode(combined);

        assertEquals(3, decoded.size());
        assertEquals(m1, decoded.get(0));
        assertEquals(m2, decoded.get(1));
        assertEquals(m3, decoded.get(2));
        assertEquals(0, decoder.bufferedBytes());
    }

    @Test
    @DisplayName("Incomplete frame should not produce a message prematurely")
    void testIncompleteFrameDoesNotProduceMessage() {
        Message original = new Message(MessageType.ANNOUNCEMENT, "ann-1");
        original.putPayload("text", "Quiz starts in 5 minutes");

        ByteBuffer fullFrame = FrameEncoder.encode(original);
        byte[] allBytes = new byte[fullFrame.remaining()];
        fullFrame.get(allBytes);

        FrameDecoder decoder = new FrameDecoder();
        List<Message> partialResult = decoder.decode(ByteBuffer.wrap(allBytes, 0, allBytes.length - 1));

        assertTrue(partialResult.isEmpty());
        assertEquals(allBytes.length - 1, decoder.bufferedBytes());
    }

    @Test
    @DisplayName("Invalid or oversized frame lengths should be rejected")
    void testInvalidAndOversizedFrameLengths() {
        FrameDecoder decoder = new FrameDecoder(128);

        // Negative length
        ByteBuffer negative = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(-5);
        negative.flip();
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(negative));

        // Zero length
        ByteBuffer zero = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0);
        zero.flip();
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(zero));

        // Oversized length
        ByteBuffer oversized = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(256);
        oversized.flip();
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(oversized));

        // FrameEncoder rejects oversized payload
        String hugeJson = "a".repeat(ProtocolFramer.MAX_FRAME_SIZE + 1);
        assertThrows(IllegalArgumentException.class, () -> FrameEncoder.encode(hugeJson));
    }

    @Test
    @DisplayName("Empty or malformed payloads should be handled safely")
    void testEmptyAndMalformedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> MessageDecoder.decode(null));
        assertThrows(IllegalArgumentException.class, () -> MessageDecoder.decode(""));
        assertThrows(IllegalArgumentException.class, () -> MessageDecoder.decode("   "));
        assertThrows(IllegalArgumentException.class, () -> MessageDecoder.decode("{not-valid-json"));

        // Malformed JSON inside a valid frame header
        byte[] badPayload = "{not-valid-json".getBytes(StandardCharsets.UTF_8);
        ByteBuffer badFrame = ByteBuffer.allocate(4 + badPayload.length).order(ByteOrder.BIG_ENDIAN);
        badFrame.putInt(badPayload.length).put(badPayload).flip();

        FrameDecoder decoder = new FrameDecoder();
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(badFrame));
    }
}
