# Architecture

See the root [`ARCHITECTURE.md`](../ARCHITECTURE.md) for the high-level architecture, module responsibilities, request flow, and networking boundaries.

## Java NIO Central Server (`server` module)

- **Startup:** `MainServer` parses an optional port argument (default `5000`), registers a JVM shutdown hook, and starts `NioServer`.
- **Event Loop:** `NioServer` opens a non-blocking `ServerSocketChannel` and a `Selector`, registers `OP_ACCEPT`, and runs a single-threaded selector loop handling `OP_ACCEPT`, `OP_READ`, and `OP_WRITE`.
- **Accepting Connections:** On `OP_ACCEPT`, `NioServer` accepts the `SocketChannel`, sets it to non-blocking, registers `OP_READ`, and attaches a `ClientConnection`.
- **Reading & Partial TCP Reads:** On `OP_READ`, `NioServer` reads available bytes into the client's `ByteBuffer` and passes them to that client's `FrameDecoder` (from `common`), which buffers incomplete frames across reads and emits complete `Message` objects to `MessageHandler`.
- **Writing:** `ClientConnection.send(Message)` encodes frames with `FrameEncoder`, queues the `ByteBuffer`, and enables `OP_WRITE` only while data is pending.
