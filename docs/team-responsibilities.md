# Team Responsibilities

The following is a proposed allocation for a five-member group. Replace placeholders with actual names and student index numbers.

| Member | Responsibility | Main deliverables |
|---|---|---|
| Member 1 | Java NIO central server | `ServerSocketChannel`, `SocketChannel`, `Selector`, `ByteBuffer`, accept/read/write and connection lifecycle |
| Member 2 | TCP classroom and polling | Classroom creation/joining, poll creation, answer submission and results over the agreed protocol |
| Member 3 | UDP and multicast | UDP heartbeat/status and multicast announcements |
| Member 4 | Multithreading and concurrency | Worker execution, concurrent client processing, safe shared poll state |
| Member 5 | JSSE/TLS security | TLS configuration, certificate/trust setup and verified handshake |

## Coordination requirements

- Member 1 owns the central server entry point and selector loop.
- Member 2 owns the core classroom/poll application operations and client request workflow.
- Member 3 coordinates datagram ports and multicast group configuration with the group.
- Member 4 agrees on the selector-thread/worker-thread boundary with Member 1.
- Member 5 coordinates TLS integration with Member 1 before connection APIs are finalised.
- Shared protocol changes must be discussed by all affected members.

These are assignments of responsibility, not claims that the features are implemented.
