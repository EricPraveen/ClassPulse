# Decision Log – ClassPulse

Record decisions that affect multiple members. Add newest decisions at the top.

## D-001 – Java and build system

- **Status:** Accepted for starter
- **Decision:** Use Java 21 and Maven.
- **Reason:** Matches the team's current environment and supports a simple multi-module Java project.
- **Impact:** All modules compile with Java release 21.

## D-002 – Multi-module structure

- **Status:** Accepted for starter
- **Decision:** Use `common`, `server`, and `client` Maven modules.
- **Reason:** Shared protocol/model classes stay separate from server and client implementation.
- **Impact:** Server and client depend on `common`.

## D-003 – Central server model

- **Status:** Proposed; confirm against assignment brief
- **Decision:** Use one central server for classroom and polling operations.
- **Reason:** Keeps the assignment workflow simple and gives the team one integration point.
- **Impact:** Members should not create separate competing central servers.

## D-004 – Console-based client

- **Status:** Assumption for initial scope
- **Decision:** Start with a console interface.
- **Reason:** The assessment focus is network programming, not UI development.
- **Impact:** Avoid spending assignment time on a GUI unless required.

## D-005 – Shared TCP communication protocol and framing

- **Status:** Accepted
- **Decision:** Use JSON application messages (`type`, `requestId`, `payload`) framed with a 4-byte big-endian integer length prefix (max 64 KB).
- **Reason:** Provides a clean, standardized, language-idiomatic protocol for all five group members to work independently without communication conflicts. Solves TCP byte-stream partial reads/writes and oversized payload vulnerabilities.
- **Impact:** All TCP networking implementations (`server`, `client`, NIO, TLS) must use `Message` and `ProtocolFramer` from the `common` module.

## Pending decisions

- TCP and UDP port numbers.
- Multicast group address and port.
- TLS certificate/trust-store approach for the demo.
- Branching and pull request workflow.

When changing an accepted decision, add a new entry explaining why; do not silently rewrite project history.
