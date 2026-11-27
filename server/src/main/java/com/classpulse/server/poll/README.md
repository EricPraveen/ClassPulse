# Poll Management and Results – ClassPulse Server

This package provides the server-side, in-memory poll management, answer submission, and result calculation services and protocol request handling for ClassPulse.

---

## Components

- **`PollService`**: In-memory repository and lifecycle manager for polls. Validates classroom existence, ensures valid questions and $\ge 2$ options, generates unique poll identifiers (`poll-xxxxxxxx`), and indexes active polls by ID and classroom code.
- **`AnswerService`**: Manages student vote submissions and calculates aggregated results. Enforces single-submission constraints per student per poll (`putIfAbsent`), validates option index bounds, computes total response counts, and calculates percentages rounded safely without division-by-zero errors.
- **`PollResult`**: Immutable model encapsulating poll outcome statistics: `pollId`, `question`, `options`, `totalResponses`, vote counts per option, and vote percentages per option. Converts seamlessly to protocol payloads via `toPayload()`.
- **`PollMessageHandler`**: Protocol adapter implementing `MessageHandler`. Dispatches incoming `CREATE_POLL`, `SUBMIT_ANSWER`, and `POLL_RESULTS` messages, handling domain validations and mapping exceptions to standard protocol error envelopes.

---

## How It Works

### 1. Poll Creation (`CREATE_POLL`)
- A lecturer submits a `CREATE_POLL` request with `classroomCode`, `question`, and `options` (minimum 2 options required).
- `PollService.createPoll(...)` verifies that the target classroom exists in `ClassroomManager` (if configured), verifies non-empty question and options, generates a unique poll ID, and stores the `Poll` model in thread-safe memory.
- The server responds with `POLL_CREATED` containing `pollId`, `classroomCode`, `question`, and confirmed `options`.

### 2. Answer Submission (`SUBMIT_ANSWER`)
- A student submits a `SUBMIT_ANSWER` request with `pollId`, `studentId`, and `selectedOption` (0-based option index).
- `AnswerService.submitAnswer(...)` verifies the poll exists and checks that `selectedOption` falls within `[0, options.size() - 1]`.
- Uses `ConcurrentHashMap.putIfAbsent(studentId, answer)` to atomically prevent double voting. If the student has already voted, `DUPLICATE_ANSWER` error is returned.
- On success, the server responds with `ANSWER_SUBMITTED` (`status: "ACCEPTED"`).

### 3. Result Calculation (`POLL_RESULTS`)
- A client requests results for a specific poll with `pollId`.
- `AnswerService.calculateResults(pollId)` aggregates all submitted votes per option index.
- Calculates percentages for each option:
  $$\text{percentage} = \begin{cases} \text{round}\left(\frac{\text{votes}}{\text{totalResponses}} \times 100\right), & \text{totalResponses} > 0 \\ 0.0, & \text{totalResponses} = 0 \end{cases}$$
- For polls with zero responses, returns `totalResponses = 0` and `0.0%` for all options without arithmetic errors or `NaN`.
- The server responds with `POLL_RESULTS` containing `pollId`, `question`, `totalResponses`, `options`, `optionResults`, and `optionPercentages`.

### 4. In-Memory Concurrency & Thread Safety
- Polls are indexed in `ConcurrentHashMap<String, Poll>` and mapped to classroom codes using `CopyOnWriteArrayList`.
- Per-poll responses are managed by `PollResponseState`, combining `ConcurrentHashMap<String, Answer>` (atomic duplicate prevention), `AtomicInteger` / `AtomicIntegerArray` vote counters, and a per-poll `ReentrantReadWriteLock` so concurrent submissions remain accurate and `calculateResults(pollId)` returns consistent snapshots.
- All state resides in-memory; no external database or persistence engine is required.

---

## Protocol Request and Response Formats

### `CREATE_POLL`
- **Request:**
  ```json
  {
    "type": "CREATE_POLL",
    "requestId": "req-101",
    "payload": {
      "classroomCode": "CS101",
      "question": "Which Java package provides NIO?",
      "options": ["java.io", "java.nio", "java.net"]
    }
  }
  ```
- **Response:**
  ```json
  {
    "type": "POLL_CREATED",
    "requestId": "req-101",
    "payload": {
      "pollId": "poll-1a2b3c4d",
      "classroomCode": "CS101",
      "question": "Which Java package provides NIO?",
      "options": ["java.io", "java.nio", "java.net"]
    }
  }
  ```

### `SUBMIT_ANSWER`
- **Request:**
  ```json
  {
    "type": "SUBMIT_ANSWER",
    "requestId": "req-102",
    "payload": {
      "pollId": "poll-1a2b3c4d",
      "studentId": "student-alice",
      "selectedOption": 1
    }
  }
  ```
- **Response:**
  ```json
  {
    "type": "ANSWER_SUBMITTED",
    "requestId": "req-102",
    "payload": {
      "pollId": "poll-1a2b3c4d",
      "status": "ACCEPTED"
    }
  }
  ```

### `POLL_RESULTS`
- **Request:**
  ```json
  {
    "type": "POLL_RESULTS",
    "requestId": "req-103",
    "payload": {
      "pollId": "poll-1a2b3c4d"
    }
  }
  ```
- **Response:**
  ```json
  {
    "type": "POLL_RESULTS",
    "requestId": "req-103",
    "payload": {
      "pollId": "poll-1a2b3c4d",
      "question": "Which Java package provides NIO?",
      "totalResponses": 1,
      "options": ["java.io", "java.nio", "java.net"],
      "optionResults": {"0": 0, "1": 1, "2": 0},
      "optionPercentages": {"0": 0.0, "1": 100.0, "2": 0.0}
    }
  }
  ```

### Error Responses
- **Non-existent classroom:** `{"type": "ERROR", "requestId": "...", "payload": {"code": "INVALID_CLASSROOM", "message": "Classroom '...' does not exist"}}`
- **Non-existent poll:** `{"type": "ERROR", "requestId": "...", "payload": {"code": "INVALID_POLL", "message": "Poll '...' does not exist"}}`
- **Invalid option index:** `{"type": "ERROR", "requestId": "...", "payload": {"code": "INVALID_OPTION", "message": "Selected option index ... is out of bounds"}}`
- **Duplicate answer:** `{"type": "ERROR", "requestId": "...", "payload": {"code": "DUPLICATE_ANSWER", "message": "Student '...' has already submitted an answer to poll '...'"}}`
- **Invalid payload / fewer than 2 options:** `{"type": "ERROR", "requestId": "...", "payload": {"code": "INVALID_PAYLOAD", "message": "Field '...' is required..."}}`

---

## Running Tests

Run all poll-related unit and integration tests from the repository root:

```bash
mvn test -pl server -Dtest="PollServiceTest,PollMessageHandlerTest,PollIntegrationTest"
```

Or run the full test suite across all modules:

```bash
mvn clean test
```
