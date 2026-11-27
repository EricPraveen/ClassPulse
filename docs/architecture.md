# Architecture

See the root [`ARCHITECTURE.md`](../ARCHITECTURE.md) for the high-level architecture, module responsibilities, request flow, and networking boundaries.

## Java NIO Central Server (`server` module)

- **Startup:** `MainServer` parses an optional port argument (default `5000`), registers a JVM shutdown hook, and starts `NioServer`.
- **Event Loop:** `NioServer` opens a non-blocking `ServerSocketChannel` and a `Selector`, registers `OP_ACCEPT`, and runs a single-threaded selector loop handling `OP_ACCEPT`, `OP_READ`, and `OP_WRITE`.
- **Accepting Connections:** On `OP_ACCEPT`, `NioServer` accepts the `SocketChannel`, sets it to non-blocking, registers `OP_READ`, and attaches a `ClientConnection`.
- **Reading & Partial TCP Reads:** On `OP_READ`, `NioServer` reads available bytes into the client's `ByteBuffer` and passes them to that client's `FrameDecoder` (from `common`), which buffers incomplete frames across reads and emits complete `Message` objects to `WorkerPool` for execution by `MessageHandler`.
- **Writing:** `ClientConnection.send(Message)` encodes frames with `FrameEncoder`, queues the `ByteBuffer`, and notifies `NioServer` via `pendingWriteRegistrations` + `selector.wakeup()` so only the selector thread mutates `SelectionKey.interestOps` and flushes writes.

## Concurrency & Thread-Safe Poll State

- **Why Multithreading Is Used:** Offloading request handling from the single NIO selector thread prevents application processing or lock contention from blocking network I/O for other connected clients.
- **Worker Execution (`WorkerPool`):** `NioServer` delegates decoded requests to a bounded fixed-size `ThreadPoolExecutor` (`WorkerPool`, `classpulse-worker-*` threads, bounded queue) and shuts it down cleanly during `NioServer.stop()`. Worker exceptions are isolated per task and reported as `INTERNAL_ERROR` without crashing the server.
- **Thread-Safe Poll State (`PollResponseState` / `AnswerService`):** Each poll's responses are guarded by `ConcurrentHashMap` (single-submission enforcement per student), `AtomicInteger` / `AtomicIntegerArray` counters, and a per-poll `ReentrantReadWriteLock` so concurrent submissions never lose votes and concurrent `POLL_RESULTS` readers observe consistent snapshots.
- **Concurrency Testing:** Verified with `WorkerPoolTest`, `PollResponseStateTest`, and `ConcurrentPollIntegrationTest` (30 concurrent TCP student submissions, duplicate rejection, non-blocking selector verification with a blocked worker, exception isolation, and pool shutdown).
