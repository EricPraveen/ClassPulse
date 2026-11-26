# Communication Protocol – To Be Agreed

## Current starter

The `common` module defines:

- `MessageType`: message categories.
- `Message`: a minimal envelope containing a type and string payload.

No serialization or network framing implementation is included yet.

## Required team decision

Before members implement client/server networking, agree on:

1. Character encoding (UTF-8 recommended).
2. Serialization format (for example, a simple delimited format or JSON).
3. TCP framing (newline-delimited messages or length-prefixed messages).
4. Maximum message size.
5. Request/response correlation strategy.
6. Error response format.
7. Exact payload fields for each message type.

## Suggested logical message types

| Type | Direction | Purpose |
|---|---|---|
| `CREATE_CLASSROOM` | Lecturer → Server | Request a classroom |
| `JOIN_CLASSROOM` | Student → Server | Join with classroom code |
| `CREATE_POLL` | Lecturer → Server | Publish a question and options |
| `SUBMIT_ANSWER` | Student → Server | Submit selected option |
| `POLL_RESULTS` | Server → Clients | Return result summary |
| `ANNOUNCEMENT` | Server → Clients | Classroom announcement |
| `HEARTBEAT` | Client ↔ Server or UDP | Presence/status demonstration |
| `ERROR` | Server → Client | Report invalid request |

## TCP reminder

TCP is a byte stream. One socket read may contain part of a message, one complete message, or multiple messages. Both sides must use the same framing implementation and handle partial reads/writes.

Do not treat the examples above as a final wire format. Update this document after the team makes its protocol decision.
