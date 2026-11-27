# Concurrency & Thread-Safe Poll Processing – ClassPulse Server

This package provides bounded worker-thread execution for the ClassPulse NIO central server (`Issue #14`).

---

## Why Multithreading Is Used in ClassPulse

ClassPulse uses a single-threaded Java NIO `Selector` event loop (`NioServer`) to accept TCP connections and perform non-blocking socket reads and writes. If application logic (classroom lookup, poll validation, answer recording, or result aggregation) ran directly on the selector thread, any slow request or lock wait would stall network I/O for every connected lecturer and student.

Offloading request processing to a bounded worker thread pool keeps the selector loop responsive while allowing multiple student requests to execute concurrently.

---

## How `ExecutorService` Processes Requests

- **`WorkerPool`** wraps a fixed-size `ThreadPoolExecutor` (`classpulse-worker-N` threads, default 4–8 based on available processors) backed by a bounded `LinkedBlockingQueue` (default capacity `1024`) with `AbortPolicy`.
- **Dispatch (`NioServer.handleRead`)**: The selector thread reads non-blocking socket bytes, decodes complete protocol frames via `FrameDecoder`, and submits each `(ClientConnection, Message)` task to `WorkerPool`.
- **Exception isolation**: Worker tasks catch unexpected exceptions without crashing the worker pool or server, returning an `INTERNAL_ERROR` response if the connection is open. If the bounded queue is full, `RejectedExecutionException` is caught and returns `SERVER_BUSY`.
- **Selector-safe response writing**: Worker threads call `ClientConnection.send(Message)`, which encodes the frame on the worker thread, appends it to the connection's synchronized write queue, and registers the connection in `NioServer`'s `ConcurrentLinkedQueue<ClientConnection>` (`pendingWriteRegistrations`) before calling `selector.wakeup()`. Only the selector thread mutates `SelectionKey.interestOps` and writes to `SocketChannel`.
- **Clean shutdown**: `NioServer.stop()` / `shutdownResources()` invokes `WorkerPool.shutdown()`, which calls `executor.shutdown()`, awaits in-flight task completion up to the timeout, and falls back to `shutdownNow()`.

---

## How Shared Poll State Is Protected

- **`PollResponseState`** protects each poll's responses independently using:
  - `ConcurrentHashMap<String, Answer>` (`putIfAbsent`) to enforce the single-submission-per-student rule (`DUPLICATE_ANSWER`).
  - `AtomicIntegerArray` for per-option vote counts and `AtomicInteger` for total responses.
  - A per-poll `ReentrantReadWriteLock`: writers (`recordAnswer`) take the write lock so the student map, option count, and total counter update atomically; readers (`snapshotResult`, `getAnswers`) take the read lock so concurrent `POLL_RESULTS` queries run in parallel and always observe a consistent snapshot (`totalResponses == sum(voteCounts)`).
- **`AnswerService`** stores `PollResponseState` instances per `pollId` in a `ConcurrentHashMap`.
- **`PollService`** stores polls in `ConcurrentHashMap` and per-classroom poll ID lists in `CopyOnWriteArrayList`.
- **`ClassroomManager`** stores classrooms in `ConcurrentHashMap` and synchronizes per-classroom student joins on the target `Classroom` instance.

---

## How Concurrent Poll Submissions Were Tested

- **`WorkerPoolTest`**: Verifies bounded fixed-size execution, named worker threads, bounded queue rejection (`RejectedExecutionException`), and clean `shutdown()` waiting for in-flight tasks.
- **`PollResponseStateTest`**: Tests 120 concurrent unique student submissions, 20 concurrent duplicate attempts from the same student (exactly 1 accepted), and concurrent readers vs. writers verifying snapshot consistency.
- **`ConcurrentPollIntegrationTest`**: Runs a real `NioServer` + `WorkerPool` over TCP sockets with 30 concurrent student connections voting simultaneously, concurrent duplicate TCP submissions, a blocked slow worker verifying the selector thread still serves fast clients immediately, worker exception isolation, and clean `WorkerPool` termination on `server.stop()`.

Run the concurrency and poll tests from the repository root:

```bash
mvn test -pl server -Dtest="WorkerPoolTest,PollResponseStateTest,ConcurrentPollIntegrationTest,PollServiceTest,PollIntegrationTest"
```
