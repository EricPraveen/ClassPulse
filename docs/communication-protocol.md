# ClassPulse Communication Protocol Specification

**Status:** Accepted
**Applies to:** All ClassPulse Modules (`common`, `server`, `client`)
**Shared Reference Implementation:** `com.classpulse.common.protocol.Message`, `MessageEncoder`, `MessageDecoder`, `FrameEncoder`, `FrameDecoder`, & `ProtocolFramer`

---

## 1. Purpose of the Protocol

ClassPulse is built by five team members working on separate network components (Java NIO server, TCP classroom/polling, UDP/multicast, concurrency, and JSSE/TLS). To prevent communication conflicts and integration friction, this document defines the universal wire protocol for client–server communication over TCP.

Every team member must use the shared `Message` envelope, `MessageType` definitions, and length-prefixed framing provided in the `common` module.

---

## 2. JSON Message Structure

All TCP application messages are exchanged as **UTF-8 encoded JSON objects**.

### Universal Message Envelope

Every application message conforms to the following JSON structure:

```json
{
  "type": "MESSAGE_TYPE",
  "requestId": "unique-request-id",
  "payload": {
    "field1": "value1",
    "field2": 123
  }
}
```

### Envelope Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `type` | String | **Yes** | The operation category matching `MessageType` enum (e.g. `CREATE_CLASSROOM`). |
| `requestId` | String | **Yes** | Unique request/transaction identifier (e.g. `req-001` or UUID). Correlates responses to requests. |
| `payload` | Object (Map) | **Yes** | Key-value dictionary containing operation-specific fields. Empty object `{}` if no fields. |

---

## 3. TCP Message Framing

### The Byte Stream Problem
TCP is a stream-oriented protocol without inherent message boundaries. A single socket read may yield:
1. **Partial header** (< 4 bytes).
2. **Partial payload** (fewer bytes than the message length).
3. **Multiple concatenated messages** received in a single network read.

### Length-Prefixed Framing Specification

To guarantee reliable message boundaries, ClassPulse uses **4-byte big-endian length-prefixed framing**:

```text
+-----------------------------+----------------------------------------------+
| Field                       | Description                                  |
+-----------------------------+----------------------------------------------+
| Length Header (4 bytes)     | Big-Endian 32-bit signed integer (N)         |
|                             | Represents the exact byte length of payload  |
+-----------------------------+----------------------------------------------+
| Payload (N bytes)           | UTF-8 encoded JSON Message string            |
+-----------------------------+----------------------------------------------+
```

### Maximum Frame Size
- **Maximum payload length:** `65,536 bytes` (`64 KB`).
- Frames with length `< 0` or `> 65536` are invalid and must be rejected immediately to protect against buffer overflow or denial-of-service attacks.

### Frame Processing Logic

#### Shared Encoder and Decoder Classes
- `MessageEncoder.encode(Message)` serializes a `Message` envelope into a UTF-8 JSON string.
- `MessageDecoder.decode(String)` deserializes a JSON string back into a validated `Message`.
- `FrameEncoder.encode(Message)` / `FrameEncoder.encode(String)` converts JSON to UTF-8 bytes, prepends the 4-byte big-endian byte length, and returns a flipped `ByteBuffer`.
- `FrameDecoder.decode(ByteBuffer)` accumulates incoming `ByteBuffer` chunks, waits until the 4-byte header and full payload are available, extracts all complete `Message` objects (including multiple frames in one buffer), preserves trailing incomplete bytes for the next read, and rejects invalid (`<= 0`) or oversized (`> 65,536` bytes) frame lengths.

#### Example Framed Message
For the JSON payload `{"type":"HEARTBEAT","requestId":"hb-1","payload":{}}` (49 UTF-8 bytes):
```text
[ 0x00 0x00 0x00 0x31 ] [ {"type":"HEARTBEAT","requestId":"hb-1","payload":{}} ]
|<-- 4-byte length -->| |<------------------ 49 UTF-8 bytes ------------------->|
```

#### For Non-Blocking NIO (Member 1 - Server):
1. In the `ByteBuffer` read loop:
2. Ensure at least 4 bytes are available. If `< 4`, wait for the next readable event.
3. Mark buffer position and read the 4-byte big-endian `length`.
4. If `length < 0` or `length > 64 KB`, close the connection or return an error.
5. If remaining bytes in buffer `< length`, reset buffer position to before the header length and wait for more socket data.
6. If remaining bytes in buffer `>= length`, read `length` bytes, decode UTF-8 string, and parse with `Message.fromJson(json)`.
7. Repeat the loop to process any additional frames in the buffer.
8. Use `ProtocolFramer.extractMessages(ByteBuffer)` which encapsulates this behavior.

#### For Blocking TCP Sockets (Member 2 - Client):
1. Use `ProtocolFramer.readMessage(InputStream)` which reads the 4-byte header via `readInt()`, followed by `readNBytes(length)`.
2. To send, call `ProtocolFramer.writeMessage(OutputStream, message)` or `ProtocolFramer.encodeFrame(message)`.

---

## 4. Message Types

| Message Type | Direction | Category | Description |
|---|---|---|---|
| `CREATE_CLASSROOM` | Client → Server | Classroom | Lecturer requests creation of a classroom. |
| `CLASSROOM_CREATED` | Server → Client | Classroom | Server confirms creation with room code. |
| `JOIN_CLASSROOM` | Client → Server | Classroom | Student requests to join a classroom session. |
| `CLASSROOM_JOINED` | Server → Client | Classroom | Server confirms student successfully joined. |
| `CREATE_POLL` | Client → Server | Polling | Lecturer publishes a new multiple-choice poll. |
| `POLL_CREATED` | Server → Client | Polling | Server confirms poll is active in classroom. |
| `GET_POLL` | Client → Server | Polling | Student/client requests details of active poll by room or poll ID. |
| `SUBMIT_ANSWER` | Client → Server | Polling | Student submits selected option to active poll. |
| `ANSWER_SUBMITTED` | Server → Client | Polling | Server acknowledges answer submission. |
| `POLL_RESULTS` | Server → Clients | Polling | Broadcast / response with aggregated poll results. |
| `ERROR` | Server → Client | General | Standard error notification. |
| `SUCCESS` | Server → Client | General | Standard success notification. |
| `DISCONNECT` | Client → Server | General | Graceful client session termination. |
| `ANNOUNCEMENT` | Server → Clients | Broadcast | Text announcement (TCP or UDP Multicast). |
| `HEARTBEAT` | Client ↔ Server | Diagnostic | Periodic keep-alive or UDP status ping. |

---

## 5. Payload Field Definitions

### 5.1 Classroom Operations

#### `CREATE_CLASSROOM` (Request)
Sent by a lecturer to start a new classroom.
```json
{
  "type": "CREATE_CLASSROOM",
  "requestId": "req-001",
  "payload": {
    "lecturerName": "Prof. Smith"
  }
}
```
- `lecturerName` (String, required): Display name of the lecturer.

#### `CLASSROOM_CREATED` (Response)
Server response confirming classroom creation.
```json
{
  "type": "CLASSROOM_CREATED",
  "requestId": "req-001",
  "payload": {
    "classroomCode": "CS101",
    "lecturerId": "user-lecturer-1"
  }
}
```
- `classroomCode` (String, required): 5–6 character alphanumeric room code.
- `lecturerId` (String, optional): Assigned lecturer user ID.

#### `JOIN_CLASSROOM` (Request)
Sent by a student to join an active classroom.
```json
{
  "type": "JOIN_CLASSROOM",
  "requestId": "req-002",
  "payload": {
    "classroomCode": "CS101",
    "studentName": "Bob"
  }
}
```
- `classroomCode` (String, required): Code of the room to join.
- `studentName` (String, required): Display name of the student.

#### `CLASSROOM_JOINED` (Response)
Server response confirming successful join.
```json
{
  "type": "CLASSROOM_JOINED",
  "requestId": "req-002",
  "payload": {
    "classroomCode": "CS101",
    "studentId": "student-123",
    "studentName": "Bob"
  }
}
```
- `classroomCode` (String, required): Confirmed room code.
- `studentId` (String, required): Server-assigned unique student identifier.
- `studentName` (String, required): Confirmed student name.

---

### 5.2 Polling Operations

#### `CREATE_POLL` (Request)
Sent by a lecturer to launch a multiple-choice poll.
```json
{
  "type": "CREATE_POLL",
  "requestId": "req-003",
  "payload": {
    "classroomCode": "CS101",
    "question": "Which Java package provides non-blocking I/O?",
    "options": [
      "java.io",
      "java.nio",
      "java.net",
      "java.util.concurrent"
    ]
  }
}
```
- `classroomCode` (String, required): Target classroom.
- `question` (String, required): The poll question text.
- `options` (Array of Strings, required): 2 to 6 multiple choice options.

#### `POLL_CREATED` (Response)
Server response confirming poll creation.
```json
{
  "type": "POLL_CREATED",
  "requestId": "req-003",
  "payload": {
    "pollId": "poll-456",
    "classroomCode": "CS101",
    "question": "Which Java package provides non-blocking I/O?",
    "options": [
      "java.io",
      "java.nio",
      "java.net",
      "java.util.concurrent"
    ]
  }
}
```
- `pollId` (String, required): Server-generated unique poll identifier.
- `classroomCode` (String, required): Associated classroom.
- `question` (String, required): Confirmed question.
- `options` (Array of Strings, required): Confirmed options list.

#### `GET_POLL` (Request)
Sent by a student or client to retrieve details of an active poll for a classroom or by poll ID.
```json
{
  "type": "GET_POLL",
  "requestId": "req-004",
  "payload": {
    "classroomCode": "CS101"
  }
}
```
- `classroomCode` (String, optional): Classroom code to query the active poll for.
- `pollId` (String, optional): Specific poll ID to query.
*Note: At least one of `classroomCode` or `pollId` must be provided.*

Server returns `POLL_CREATED` containing `pollId`, `classroomCode`, `question`, and `options` on success, or `ERROR` (`NO_ACTIVE_POLL`, `INVALID_CLASSROOM`, `INVALID_POLL`, `INVALID_PAYLOAD`).

#### `SUBMIT_ANSWER` (Request)
Sent by a student to submit an answer.
```json
{
  "type": "SUBMIT_ANSWER",
  "requestId": "req-004",
  "payload": {
    "classroomCode": "CS101",
    "pollId": "poll-456",
    "studentId": "student-123",
    "selectedOption": 1
  }
}
```
- `classroomCode` (String, required): Classroom code.
- `pollId` (String, required): Active poll ID.
- `studentId` (String, required): Submitting student's ID.
- `selectedOption` (Integer, required): 0-based index of chosen option.

#### `ANSWER_SUBMITTED` (Response)
Server acknowledgement of submitted answer.
```json
{
  "type": "ANSWER_SUBMITTED",
  "requestId": "req-004",
  "payload": {
    "pollId": "poll-456",
    "status": "ACCEPTED"
  }
}
```
- `pollId` (String, required): Target poll ID.
- `status` (String, required): "ACCEPTED" or "RECORDED".

#### `POLL_RESULTS` (Broadcast / Response)
Sent by the server to return current or final aggregated poll results.
```json
{
  "type": "POLL_RESULTS",
  "requestId": "req-005",
  "payload": {
    "pollId": "poll-456",
    "question": "Which Java package provides non-blocking I/O?",
    "totalResponses": 25,
    "optionResults": {
      "0": 1,
      "1": 22,
      "2": 2,
      "3": 0
    }
  }
}
```
- `pollId` (String, required): Target poll ID.
- `question` (String, required): Question text.
- `totalResponses` (Integer, required): Total number of recorded responses.
- `optionResults` (Map<String, Integer>, required): Counts mapped by option index.

---

### 5.3 General Operations

#### `ERROR` (Response)
Universal error response returned when an operation cannot be completed.
```json
{
  "type": "ERROR",
  "requestId": "req-001",
  "payload": {
    "code": "INVALID_CLASSROOM",
    "message": "Classroom code 'CS999' was not found or is closed"
  }
}
```
- `code` (String, required): Machine-readable error code.
- `message` (String, required): Human-readable error description.

**Standard Error Codes:**
- `INVALID_CLASSROOM`: Classroom code does not exist or has ended.
- `INVALID_POLL`: Poll ID not found or already closed.
- `INVALID_OPTION`: Selected option index is out of bounds.
- `DUPLICATE_ANSWER`: Student has already submitted an answer to this poll.
- `UNAUTHORIZED`: Client not permitted to perform operation (e.g. non-lecturer creating poll).
- `INVALID_PAYLOAD`: Missing or improperly formatted payload fields.
- `MALFORMED_MESSAGE`: Unparseable JSON or invalid message envelope.
- `FRAME_TOO_LARGE`: Frame exceeds maximum allowed size (64 KB).
- `INTERNAL_ERROR`: Unexpected internal server error.

#### `SUCCESS` (Response)
Generic success response.
```json
{
  "type": "SUCCESS",
  "requestId": "req-010",
  "payload": {
    "message": "Operation completed successfully"
  }
}
```

#### `DISCONNECT` (Notification)
Sent by client prior to closing socket connection.
```json
{
  "type": "DISCONNECT",
  "requestId": "req-099",
  "payload": {
    "reason": "User exited application"
  }
}
```

---

## 6. Example Request–Response Exchanges

### Scenario A: Lecturer Creates Classroom and Poll
```text
Lecturer Client                          ClassPulse Central Server
     |                                              |
     |--- [Header: 68 bytes] ---------------------->|
     |    {"type":"CREATE_CLASSROOM",               |
     |     "requestId":"req-1",                     |
     |     "payload":{"lecturerName":"Prof. Jones"}}|
     |                                              |
     |<-- [Header: 72 bytes] -----------------------|
     |    {"type":"CLASSROOM_CREATED",              |
     |     "requestId":"req-1",                     |
     |     "payload":{"classroomCode":"NET101"}}    |
     |                                              |
     |--- [Header: 184 bytes] --------------------->|
     |    {"type":"CREATE_POLL",                    |
     |     "requestId":"req-2",                     |
     |     "payload":{"classroomCode":"NET101",     |
     |      "question":"Is UDP connectionless?",    |
     |      "options":["Yes","No"]}}                |
     |                                              |
     |<-- [Header: 196 bytes] ----------------------|
     |    {"type":"POLL_CREATED",                   |
     |     "requestId":"req-2",                     |
     |     "payload":{"pollId":"p-1",               |
     |      "classroomCode":"NET101", ...}}         |
```

### Scenario B: Student Joins and Submits Answer
```text
Student Client                           ClassPulse Central Server
     |                                              |
     |--- [Header: 95 bytes] ---------------------->|
     |    {"type":"JOIN_CLASSROOM",                 |
     |     "requestId":"req-s1",                    |
     |     "payload":{"classroomCode":"NET101",     |
     |      "studentName":"Charlie"}}               |
     |                                              |
     |<-- [Header: 121 bytes] ----------------------|
     |    {"type":"CLASSROOM_JOINED",               |
     |     "requestId":"req-s1",                    |
     |     "payload":{"classroomCode":"NET101",     |
     |      "studentId":"s-77","studentName":"Charlie"}}
     |                                              |
     |--- [Header: 110 bytes] --------------------->|
     |    {"type":"SUBMIT_ANSWER",                  |
     |     "requestId":"req-s2",                    |
     |     "payload":{"classroomCode":"NET101",     |
     |      "pollId":"p-1","studentId":"s-77",      |
     |      "selectedOption":0}}                    |
     |                                              |
     |<-- [Header: 75 bytes] -----------------------|
     |    {"type":"ANSWER_SUBMITTED",               |
     |     "requestId":"req-s2",                    |
     |     "payload":{"pollId":"p-1",               |
     |      "status":"ACCEPTED"}}                   |
```

---

## 7. How All Five Team Members Use the Shared Protocol

| Role | Member Responsibilities | Protocol Usage |
|---|---|---|
| **Member 1** | **Java NIO Central Server** | Manages Selector event loop. Decodes incoming channel bytes using `ProtocolFramer.extractMessages(byteBuffer)`. Encodes outgoing frames with `ProtocolFramer.encodeFrame(message)`. Handles partial reads/writes transparently. |
| **Member 2** | **TCP Classroom & Polling** | Implements application business logic. Dispatches on `message.getType()`. Reads payload values via `message.getPayloadString()`, `getPayloadInt()`, etc. Constructs replies using `Message.response()` and `Message.error()`. |
| **Member 3** | **UDP Heartbeat & Multicast** | Uses lightweight datagrams or reuses `Message` JSON encoding for `MessageType.HEARTBEAT` and `MessageType.ANNOUNCEMENT` broadcasts across student nodes. |
| **Member 4** | **Concurrency & Threading** | Dispatches incoming `Message` envelopes to worker thread pools. Synchronizes concurrent answer submissions (`SUBMIT_ANSWER`) on shared poll state and aggregates results for `POLL_RESULTS`. |
| **Member 5** | **JSSE / TLS Security** | Establishes secure TLS session via `SSLEngine` or `SSLSocket`. The application layer protocol remains completely unchanged: `ProtocolFramer` operates over the TLS decrypted stream. |

---

## 8. Rules for Proposing Protocol Changes

To ensure ongoing alignment across all team members:

1. **Envelope immutability:** Do not change or remove `type`, `requestId`, or `payload` envelope fields.
2. **Backward-compatible payload extensions:** You may add new optional fields to a payload. Existing code must ignore unrecognized fields.
3. **Adding new MessageTypes:**
   - Discuss and agree with the team beforehand.
   - Add the enum constant to `MessageType.java`.
   - Document request and response payloads in this document.
   - Record the decision in `DECISIONS.md`.
4. **Framing rule:** The 4-byte big-endian length prefix and 64 KB maximum frame size are fixed and must not be altered independently.
