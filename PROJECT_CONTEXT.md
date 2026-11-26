# Project Context – ClassPulse

## 1. Project summary

ClassPulse is a Java client–server application for simple real-time classroom polling. A lecturer creates a classroom and a poll. Students join with a classroom code and submit answers. The server collects responses and calculates poll results.

## 2. Problem

In a classroom, collecting answers verbally or by a show of hands can be slow and difficult to summarise. ClassPulse provides a small networked workflow for collecting student responses during a lesson.

## 3. Intended users

- **Lecturer:** creates a classroom, publishes a poll, and views results.
- **Student:** joins a classroom and submits an answer.

These are application roles, not account-management requirements. Authentication and persistent user accounts are outside the initial starter scope unless required by the assignment.

## 4. Goals

1. Implement a working Java client–server application.
2. Demonstrate the network programming concepts assigned to the group.
3. Support classroom creation and joining.
4. Support poll creation, answer submission, and result calculation.
5. Demonstrate multiple connected clients.
6. Keep implementation and testing small enough for the assignment.

## 5. Initial functional requirements

| ID | Requirement |
|---|---|
| FR-01 | Lecturer can create a classroom. |
| FR-02 | Server returns a classroom code. |
| FR-03 | Student can join using a valid classroom code. |
| FR-04 | Lecturer can create a multiple-choice poll. |
| FR-05 | Student can submit an answer to an active poll. |
| FR-06 | Server records answers and calculates poll results. |
| FR-07 | Server can handle multiple connected clients. |
| FR-08 | The system demonstrates UDP heartbeat/status messages. |
| FR-09 | The system demonstrates multicast announcements. |
| FR-10 | The system demonstrates concurrent request processing. |
| FR-11 | The system demonstrates a JSSE/TLS connection. |

The group should confirm the exact mandatory requirements against the official assignment specification. Do not add optional features unless time permits and they support the assessment.

## 6. Non-functional constraints

- Java 21 source compatibility.
- Maven multi-module project.
- Shared protocol/model definitions in `common`.
- No hard-coded machine-specific paths or secrets.
- Keep the application runnable from the command line.
- Handle disconnects and malformed messages without terminating the whole server.
- Document network ports and multicast settings once agreed.

## 7. Scope

### Included in the assignment-sized scope

- Lecturer/student console workflows.
- Central server.
- Classroom creation and joining.
- Basic multiple-choice polling.
- Answer collection and result summary.
- NIO, TCP, UDP, multicast, concurrency, and TLS demonstrations as allocated to the team.

### Not included by default

- Database or permanent storage.
- Web/mobile UI.
- Cloud deployment.
- Complex authentication/authorisation.
- Analytics, dashboards, or advanced reporting.
- Production-grade scaling or high availability.

## 8. Assumptions

- The team has five members and will use the responsibility split in `docs/team-responsibilities.md`.
- The project uses Java 21 and Maven.
- A console interface is acceptable for the initial implementation.
- The central server is the coordination point for classroom and polling operations.
- TCP message framing and message payload format must be agreed by the group before implementation.
- UDP and multicast are used only for small demonstrations such as heartbeat/status and announcements.
- TLS will be integrated with the central connection design; the NIO and security owners must coordinate early.

## 9. Open questions

- What exact network concepts and deliverables are mandatory in the official assignment brief?
- What TCP framing format will the group use: newline-delimited text/JSON or length-prefixed messages?
- Which TCP and UDP ports will be used?
- What multicast group address and port are suitable for the lab network?
- Does the lecturer expect TLS on the main client-server connection or a separate demonstration?
- Are screenshots, packet captures, or a live demonstration required?

Record decisions in `DECISIONS.md`.
