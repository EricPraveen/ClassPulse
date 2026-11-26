# Lecturer Console Workflow – ClassPulse Client

This package provides the console interface and networking client for the **Lecturer** role in the ClassPulse real-time classroom polling application.

---

## Components

- **`LecturerMain`**: Dedicated executable entry point for launching the lecturer console with optional `[host] [port]` arguments.
- **`LecturerConsoleApp`**: Interactive, menu-driven terminal interface handling user input, input validation, formatted display, and error handling. Uses `InputStream` and `PrintStream` abstractions to allow automated end-to-end testing without interactive user input.
- **`LecturerClient`**: Network service layer encapsulating `TcpClient`. Formulates protocol request messages (`CREATE_CLASSROOM`, `CREATE_POLL`, `POLL_RESULTS`), correlates request IDs, and updates the local session state upon receiving successful server responses.
- **`LecturerSession`**: In-memory session model tracking the active classroom code, lecturer identity, and created polls during the console session.
- **`PollSummary`**: Immutable descriptor of a poll created during the current session (`pollId`, `question`, `options`).

---

## Interactive Workflow & Features

### 1. Connection Management
- Automatically connects to the ClassPulse NIO server upon startup.
- Displays connection status in the persistent console status bar (`[Connected]` / `[Disconnected]`).
- If disconnected or the server restarts, menu option `5. Reconnect to Server` allows reconnecting cleanly without terminating the console application.

### 2. Classroom Creation (`CREATE_CLASSROOM`)
- Menu option `1. Create a Classroom` prompts for the lecturer's name.
- Validates that the name is not blank before sending the request.
- Sends a `CREATE_CLASSROOM` request to the server and parses the `CLASSROOM_CREATED` response.
- Displays the generated classroom join code (e.g. `CP8X2K`) and stores it as the active room in `LecturerSession`.

### 3. Poll Creation (`CREATE_POLL`)
- Menu option `2. Create a Poll` detects whether an active classroom exists in the current session.
  - If active, allows using it with a single Enter press or entering an alternative code.
  - If no active classroom, prompts for the classroom join code.
- Prompts for the poll question.
- Collects multiple-choice answer options interactively (minimum 2 required).
- Sends `CREATE_POLL` to the server and displays the confirmed `pollId` and options list upon `POLL_CREATED`.
- Saves the created poll into the session's poll history.

### 4. Real-Time Poll Results (`POLL_RESULTS`)
- Menu option `3. View Poll Results` displays recent polls created in this session for quick 1-key selection, or prompts for any target `pollId`.
- Sends `POLL_RESULTS` to the server.
- Formats and prints:
  - Total student responses received.
  - Vote counts and formatted percentages per option:
    ```
    Option [0]: Quicksort               |  15 vote(s) ( 75.0%)
    Option [1]: Mergesort               |   5 vote(s) ( 25.0%)
    ```
  - Displays a clean notice when `totalResponses == 0` without arithmetic exceptions or crashes:
    ```
    [Notice] No responses have been submitted yet.
    ```

### 5. Session Information & Diagnostics
- Menu option `4. View Session Information` prints current session metadata:
  - Lecturer display name and ID.
  - Active classroom code.
  - Server connection address and status.
  - Complete list of polls created in the current session.

### 6. Robust Error & Disconnect Handling
- Validates all user inputs, option bounds, and required fields.
- Catches server `ERROR` responses (e.g. `INVALID_CLASSROOM`, `INVALID_POLL`, `INVALID_PAYLOAD`) and displays informative feedback without crashing.
- Catches network socket disconnects (`ClientTransportException`), alerts the lecturer, and preserves state so the lecturer can reconnect using option 5.

---

## How to Run

### Via the Main Client Starter
```bash
# Run with role selection menu:
java -jar client/target/client-1.0-SNAPSHOT.jar

# Or launch directly into lecturer mode:
java -jar client/target/client-1.0-SNAPSHOT.jar --lecturer
```

### Directly via Maven Exec / Java
```bash
# From repository root with default host/port (127.0.0.1:5000):
java -cp client/target/client-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar com.classpulse.client.lecturer.LecturerMain

# With custom host and port:
java -cp client/target/client-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar com.classpulse.client.lecturer.LecturerMain 127.0.0.1 5000
```

---

## Running Automated Tests

Run the lecturer test suite from the repository root:

```bash
mvn test -pl client -Dtest="LecturerClientTest,LecturerConsoleAppTest"
```

Or run all tests across all modules:

```bash
mvn clean test
```
