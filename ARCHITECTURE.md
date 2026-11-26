# Architecture – ClassPulse

## 1. High-level design

ClassPulse uses a centralised client–server architecture. Lecturer and student clients connect to one central server. The server coordinates classroom membership and poll state.

```text
        Lecturer Client             Student Client(s)
              |                           |
              +-------------+-------------+
                            |
                    TCP / TLS connection
                            |
                    +---------------+
                    | ClassPulse    |
                    | Central Server|
                    +---------------+
                      |     |     |
                     NIO   TCP   Worker tasks
                      |
             +---------------------+
             | Classroom / Poll    |
             | application state   |
             +---------------------+

        UDP heartbeat/status and multicast announcements
        are separate datagram-based demonstrations.
```

The diagram is conceptual. Update it if the final implementation uses a different connection arrangement.

## 2. Maven modules

### `common`

Contains types shared by the client and server:

- `model`: User, Classroom, Poll, Answer
- `protocol`: Message, MessageType

Keep this module free of server-specific and client-specific implementation details.

### `server`

Contains the central server and networking/application responsibilities:

- `nio`: non-blocking server and connection handling
- `tcp`: TCP request handling for classroom and poll operations
- `udp`: heartbeat/status datagrams
- `multicast`: announcement sender/receiver
- `concurrency`: worker execution and thread-safe shared state
- `security`: JSSE/TLS setup
- `classroom`: classroom operations
- `poll`: poll and answer operations

These are package boundaries, not completed implementations.

### `client`

Contains the client entry point and user-side workflows:

- `network`: server connection and message exchange
- `lecturer`: lecturer console actions
- `student`: student console actions

## 3. Main request flow

1. Client connects to the server.
2. Client sends a framed request using the agreed protocol.
3. Server decodes the message and identifies its `MessageType`.
4. Relevant application logic validates and processes the request.
5. Server sends a response to the requesting client.
6. For poll results or announcements, the server may notify multiple clients as designed.

## 4. NIO and concurrency boundary

The NIO selector thread should perform non-blocking channel I/O and lightweight dispatch. Potentially blocking or longer-running work should be delegated to worker threads if the team adopts that design. Define how worker results are returned to the selector thread before implementing it.

Shared classroom and poll state must be protected against concurrent updates. Choose simple thread-safe structures or synchronisation appropriate to the implementation.

## 5. TCP framing

TCP is a byte stream. A single read is not guaranteed to contain exactly one complete application message. The team must implement and test a framing strategy consistently on both client and server.

Document the selected framing format, maximum message size, encoding, and malformed-message behaviour in `docs/communication-protocol.md`.

## 6. UDP and multicast

UDP heartbeat/status messages and multicast announcements are datagrams, separate from the TCP request/response stream. Document destination ports, multicast group, TTL/interface assumptions, and what happens if multicast is unavailable on the test network.

## 7. TLS integration

JSSE/TLS changes how the client and server establish and use their transport connection. The security owner must coordinate with the NIO owner before the connection API is finalised. Do not claim TLS protection until a client and server have completed a verified TLS handshake.

## 8. Error handling and shutdown

At minimum, plan for:

- Client disconnects.
- Invalid or incomplete messages.
- Unknown message types.
- Invalid classroom codes.
- Invalid poll answers.
- Server shutdown and channel/socket cleanup.
- Worker task failures.

## 9. Architecture status

This document describes the intended starter architecture. Update it to match the actual implementation and remove any feature that the team does not implement.
