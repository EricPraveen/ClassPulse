# Student Console Workflow – ClassPulse Client

This package provides the console interface and networking client for the **Student** role in the ClassPulse real-time classroom polling application.

---

## Components

- **`StudentMain`**: Standalone executable entry point for launching the student console with optional `[host] [port]` arguments.
- **`StudentConsoleApp`**: Interactive, menu-driven terminal interface handling user input, input validation, formatted displays, and error handling. Uses `InputStream` and `PrintStream` abstractions to allow automated end-to-end testing without human keyboard input.
- **`StudentClient`**: Network service layer encapsulating `TcpClient`. Formulates protocol request messages (`JOIN_CLASSROOM`, `GET_POLL`, `SUBMIT_ANSWER`), correlates request IDs, and updates local session state upon receiving confirmed server responses.
- **`StudentSession`**: In-memory session model tracking the student display name, assigned student ID, enrolled classroom code, active poll details, and submitted answer status.

---

## Interactive Workflow & Features

### 1. Connection Management
- Automatically connects to the ClassPulse NIO server upon startup.
- Displays connection status in the persistent console status bar (`[Connected]` / `[Disconnected]`).
- Menu option `6. Reconnect to Server` allows reconnecting cleanly without application crashes if the server restarts or network drops.

### 2. Setting Display Name
- Menu option `1. Set / Change Display Name` allows the student to configure or change their name at any time.
- Validates that display names are non-empty before storing or sending.

### 3. Joining a Classroom (`JOIN_CLASSROOM`)
- Menu option `2. Join a Classroom` prompts for the classroom join code (e.g. `CP8X2K`).
- Automatically prompts for a student display name if not yet entered.
- Sends a `JOIN_CLASSROOM` request to the server and parses the `CLASSROOM_JOINED` response.
- Displays the confirmed room code and server-assigned unique `studentId`, updating `StudentSession`.
- Handles non-existent classrooms cleanly by displaying the server error message (`INVALID_CLASSROOM`).

### 4. Viewing the Active Poll (`GET_POLL`)
- Menu option `3. View Active Poll` requests the currently active poll for the student's enrolled classroom.
- Displays the poll question and numbered choices `[0] ... [N-1]`.
- If no poll has been published yet by the lecturer, displays a clear notice (`[Notice] No active poll is currently available in classroom...`) without errors.
- If the student has already voted, shows their recorded answer.

### 5. Submitting an Answer (`SUBMIT_ANSWER`)
- Menu option `4. Submit Answer to Poll` presents the active poll and prompts for an option index.
- If the active poll was not already fetched, automatically retrieves it.
- Client-side validation verifies that the input is a valid number within `0 <= selection < options.size()`.
- Sends `SUBMIT_ANSWER` containing `classroomCode`, `pollId`, `studentId`, and `selectedOption`.
- Confirms submission only when `ANSWER_SUBMITTED` (`status: "ACCEPTED"`) is received from the server.
- Prevents duplicate voting: subsequent submission attempts are blocked both locally and by server `DUPLICATE_ANSWER` error enforcement.

### 6. Session Status & Safe Exit
- Menu option `5. View Session Status` displays student credentials, enrolled room, active poll, and vote status.
- Menu option `7. Exit` gracefully closes socket connections, frees resources, and exits the loop.

---

## How to Run

### Via Main Client Starter
```bash
# Launch interactive role menu:
java -jar client/target/client-1.0-SNAPSHOT.jar

# Or launch directly into student mode:
java -jar client/target/client-1.0-SNAPSHOT.jar --student
```

### Directly via Executable Entry Point
```bash
# Default host/port (127.0.0.1:5000):
java -cp client/target/client-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar com.classpulse.client.student.StudentMain

# Custom host and port:
java -cp client/target/client-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar com.classpulse.client.student.StudentMain 127.0.0.1 5000
```

---

## Running Automated Tests

Run the student test suite from the repository root:

```bash
mvn test -pl client -Dtest="StudentClientTest,StudentConsoleAppTest"
```

Or run all tests across the entire repository:

```bash
mvn clean test
```
