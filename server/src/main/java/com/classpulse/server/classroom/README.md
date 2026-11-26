# Classroom Management – ClassPulse Server

This package provides the server-side, in-memory classroom management service and protocol request handling for ClassPulse.

---

## Components

- **`ClassroomManager`**: In-memory repository and business service managing active classrooms and student memberships safely across concurrent client threads.
- **`JoinResult`**: Outcome model returned by join operations containing success/failure state, error code (`INVALID_CLASSROOM`, `DUPLICATE_STUDENT`, `INVALID_PAYLOAD`), and enrolled student data.
- **`ClassroomMessageHandler`**: Protocol adapter implementing `MessageHandler`. Dispatches incoming `CREATE_CLASSROOM` and `JOIN_CLASSROOM` messages and formats protocol-compliant response envelopes.

---

## How It Works

### 1. Classroom Creation
- A lecturer sends a `CREATE_CLASSROOM` request with `lecturerName`.
- `ClassroomManager.createClassroom(lecturerName)` generates a non-colliding 6-character alphanumeric code (e.g. `CP8X2K`), assigns a unique lecturer ID, and creates a `Classroom` model.
- The classroom is stored in a thread-safe `ConcurrentHashMap`.
- The server responds with `CLASSROOM_CREATED` containing the assigned `classroomCode` and `lecturerId`.

### 2. Student Joining
- A student sends a `JOIN_CLASSROOM` request with `classroomCode` and `studentName`.
- `ClassroomManager.joinClassroom(code, studentName)` normalizes the code to uppercase, checks for classroom existence, and synchronizes on the classroom instance.
- Duplicate joins (same student name in the same classroom) are prevented with a `DUPLICATE_STUDENT` error response.
- On success, a unique student ID is generated, the student is added as a `User` (role `STUDENT`) to the classroom's student list, and the server responds with `CLASSROOM_JOINED`.

### 3. In-Memory Concurrency & Storage
- Classrooms are mapped by uppercase join code in a `ConcurrentHashMap<String, Classroom>`.
- Individual classroom modifications (student enrollments) are synchronized on the respective `Classroom` instance, guaranteeing atomic duplicate checks and list additions under heavy concurrent traffic.
- No database or persistent storage is required; state remains active for the duration of the server process.

---

## Protocol Request and Response Formats

### `CREATE_CLASSROOM`
- **Request:** `{"type": "CREATE_CLASSROOM", "requestId": "req-1", "payload": {"lecturerName": "Prof. Smith"}}`
- **Response:** `{"type": "CLASSROOM_CREATED", "requestId": "req-1", "payload": {"classroomCode": "CS101", "lecturerId": "lecturer-abc"}}`

### `JOIN_CLASSROOM`
- **Request:** `{"type": "JOIN_CLASSROOM", "requestId": "req-2", "payload": {"classroomCode": "CS101", "studentName": "Bob"}}`
- **Response:** `{"type": "CLASSROOM_JOINED", "requestId": "req-2", "payload": {"classroomCode": "CS101", "studentId": "student-xyz", "studentName": "Bob"}}`

### Error Responses
- **Non-existent classroom:** `{"type": "ERROR", "requestId": "...", "payload": {"code": "INVALID_CLASSROOM", "message": "Classroom '...' does not exist"}}`
- **Duplicate student:** `{"type": "ERROR", "requestId": "...", "payload": {"code": "DUPLICATE_STUDENT", "message": "Student '...' has already joined classroom '...'"}}`
- **Missing payload fields:** `{"type": "ERROR", "requestId": "...", "payload": {"code": "INVALID_PAYLOAD", "message": "Field '...' is required and cannot be blank"}}`

---

## Running Tests

Run all unit and integration tests from the repository root:

```bash
mvn test -pl server
```

Or run the full test suite across all modules:

```bash
mvn clean test
```
