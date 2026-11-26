package com.classpulse.common.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {

    @Test
    @DisplayName("Should create valid message with payload and preserve fields")
    void testValidMessageCreation() {
        Message msg = new Message(MessageType.CREATE_CLASSROOM, "req-001");
        msg.putPayload("lecturerName", "Alice");

        assertEquals(MessageType.CREATE_CLASSROOM, msg.getType());
        assertEquals("req-001", msg.getRequestId());
        assertEquals("Alice", msg.getPayloadString("lecturerName"));
        assertEquals("Alice", msg.getPayloadField("lecturerName"));
    }

    @Test
    @DisplayName("Should preserve request ID across serialization and deserialization")
    void testRequestIdPreservation() {
        String testRequestId = "req-uuid-98765-abcd";
        Message original = new Message(MessageType.JOIN_CLASSROOM, testRequestId);
        original.putPayload("studentName", "Bob");
        original.putPayload("classroomCode", "CS101");

        String json = original.toJson();
        Message deserialized = Message.fromJson(json);

        assertEquals(testRequestId, deserialized.getRequestId());
        assertEquals(original, deserialized);
    }

    @Test
    @DisplayName("Should correctly serialize and deserialize all MessageType values")
    void testAllMessageTypesSerialization() {
        for (MessageType type : MessageType.values()) {
            Message original = new Message(type, "req-" + type.name());
            original.putPayload("typeCheck", type.name());

            String json = original.toJson();
            Message deserialized = Message.fromJson(json);

            assertEquals(type, deserialized.getType());
            assertEquals("req-" + type.name(), deserialized.getRequestId());
            assertEquals(type.name(), deserialized.getPayloadString("typeCheck"));
        }
    }

    @Test
    @DisplayName("Should reject messages with missing required fields")
    void testMissingRequiredFields() {
        // Missing type
        Message noType = new Message(null, "req-123");
        assertThrows(IllegalArgumentException.class, noType::validate);
        assertThrows(IllegalArgumentException.class, noType::toJson);

        // Missing request ID
        Message noRequestId = new Message(MessageType.ERROR, null);
        assertThrows(IllegalArgumentException.class, noRequestId::validate);
        assertThrows(IllegalArgumentException.class, noRequestId::toJson);

        Message blankRequestId = new Message(MessageType.ERROR, "   ");
        assertThrows(IllegalArgumentException.class, blankRequestId::validate);

        // Null or empty JSON parsing
        assertThrows(IllegalArgumentException.class, () -> Message.fromJson(null));
        assertThrows(IllegalArgumentException.class, () -> Message.fromJson(""));
        assertThrows(IllegalArgumentException.class, () -> Message.fromJson("   "));

        // JSON missing required envelope fields
        assertThrows(IllegalArgumentException.class, () -> Message.fromJson("{\"payload\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> Message.fromJson("{\"type\":\"ERROR\"}"));
    }

    @Test
    @DisplayName("Should reject malformed JSON syntax")
    void testMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> Message.fromJson("{invalid-json"));
    }

    @Test
    @DisplayName("Should safely parse diverse payload data types")
    void testPayloadDataTypes() {
        Message msg = new Message(MessageType.POLL_RESULTS, "req-poll-1");
        msg.putPayload("question", "What is Java NIO?");
        msg.putPayload("totalResponses", 42);
        msg.putPayload("averageRating", 4.8);
        msg.putPayload("isActive", true);
        msg.putPayload("options", List.of("Option A", "Option B", "Option C"));

        String json = msg.toJson();
        Message parsed = Message.fromJson(json);

        assertEquals("What is Java NIO?", parsed.getPayloadString("question"));
        assertEquals(42, parsed.getPayloadInt("totalResponses"));
        assertEquals(4.8, parsed.getPayloadDouble("averageRating"), 0.001);
        assertTrue(parsed.getPayloadBoolean("isActive"));

        List<String> options = parsed.getPayloadList("options");
        assertNotNull(options);
        assertEquals(3, options.size());
        assertEquals("Option A", options.get(0));
    }

    @Test
    @DisplayName("Should construct standard error and success responses")
    void testStandardErrorAndSuccessResponses() {
        Message err = Message.error("req-002", "INVALID_CLASSROOM", "Classroom does not exist");
        assertEquals(MessageType.ERROR, err.getType());
        assertEquals("req-002", err.getRequestId());
        assertEquals("INVALID_CLASSROOM", err.getPayloadString("code"));
        assertEquals("Classroom does not exist", err.getPayloadString("message"));

        Message succ = Message.success("req-003", "Poll created successfully");
        assertEquals(MessageType.SUCCESS, succ.getType());
        assertEquals("req-003", succ.getRequestId());
        assertEquals("Poll created successfully", succ.getPayloadString("message"));
    }

    @Test
    @DisplayName("Should correctly encode and decode length-prefixed frames")
    void testLengthPrefixedFraming() {
        Message original = new Message(MessageType.CREATE_POLL, "req-77");
        original.putPayload("classroomCode", "ROOM1");
        original.putPayload("question", "Ready?");

        byte[] frameBytes = ProtocolFramer.encodeFrame(original);

        // Header check: 4 bytes big endian matching payload length
        ByteBuffer buf = ByteBuffer.wrap(frameBytes).order(ByteOrder.BIG_ENDIAN);
        int length = buf.getInt();
        assertEquals(frameBytes.length - 4, length);

        Message decoded = ProtocolFramer.decodeMessage(frameBytes);
        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("Should reject oversized frames exceeding 64 KB")
    void testOversizedFrameRejection() {
        // String exceeding 64 KB (65,536 bytes)
        String hugePayload = "A".repeat(ProtocolFramer.MAX_FRAME_SIZE + 1);

        assertThrows(IllegalArgumentException.class, () -> ProtocolFramer.encodeFrame(hugePayload));

        // Crafting a fake oversized frame header
        ByteBuffer fakeHeader = ByteBuffer.allocate(ProtocolFramer.HEADER_SIZE + 10);
        fakeHeader.order(ByteOrder.BIG_ENDIAN);
        fakeHeader.putInt(ProtocolFramer.MAX_FRAME_SIZE + 1); // Length claims > 64 KB
        fakeHeader.put(new byte[10]);

        assertThrows(IllegalArgumentException.class, () -> ProtocolFramer.decodeFrame(fakeHeader.array()));
    }

    @Test
    @DisplayName("Should support blocking stream reading and writing")
    void testStreamReadAndWrite() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Message original = Message.request(MessageType.SUBMIT_ANSWER, "req-sub-1");
        original.putPayload("pollId", "p-100");
        original.putPayload("selectedOption", 2);

        ProtocolFramer.writeMessage(out, original);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Message read = ProtocolFramer.readMessage(in);

        assertNotNull(read);
        assertEquals(original, read);
        assertEquals(2, read.getPayloadInt("selectedOption"));

        // End of stream returns null gracefully
        Message eof = ProtocolFramer.readMessage(in);
        assertNull(eof);
    }

    @Test
    @DisplayName("Should handle partial headers, partial payloads, and multiple frames in NIO buffer")
    void testNioBufferFraming() {
        Message msg1 = Message.request(MessageType.CREATE_CLASSROOM, "req-1");
        msg1.putPayload("lecturerName", "Prof. Smith");
        Message msg2 = Message.request(MessageType.JOIN_CLASSROOM, "req-2");
        msg2.putPayload("studentName", "Student Doe");

        byte[] frame1 = ProtocolFramer.encodeFrame(msg1);
        byte[] frame2 = ProtocolFramer.encodeFrame(msg2);

        // Concatenate frame1 and frame2
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(frame1);
        buffer.put(frame2);
        buffer.flip();

        List<Message> messages = ProtocolFramer.extractMessages(buffer);
        assertEquals(2, messages.size());
        assertEquals(msg1, messages.get(0));
        assertEquals(msg2, messages.get(1));
        assertEquals(0, buffer.remaining());

        // Test partial header (< 4 bytes)
        ByteBuffer partialHeaderBuf = ByteBuffer.allocate(16);
        partialHeaderBuf.put(new byte[]{0, 0}); // only 2 bytes
        partialHeaderBuf.flip();
        List<Message> partialHeaderResult = ProtocolFramer.extractMessages(partialHeaderBuf);
        assertTrue(partialHeaderResult.isEmpty());
        assertEquals(2, partialHeaderBuf.remaining()); // buffer position preserved

        // Test partial payload
        ByteBuffer partialPayloadBuf = ByteBuffer.allocate(64);
        partialPayloadBuf.order(ByteOrder.BIG_ENDIAN);
        partialPayloadBuf.putInt(20); // Claims 20 bytes payload
        partialPayloadBuf.put("Partial-".getBytes(StandardCharsets.UTF_8)); // only 8 bytes
        partialPayloadBuf.flip();

        List<Message> partialPayloadResult = ProtocolFramer.extractMessages(partialPayloadBuf);
        assertTrue(partialPayloadResult.isEmpty());
        assertEquals(12, partialPayloadBuf.remaining()); // 4 (header) + 8 (data) preserved
    }
}
