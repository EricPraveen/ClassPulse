# ClassPulse

[![CI](https://github.com/EricPraveen/ClassPulse/actions/workflows/ci.yml/badge.svg)](https://github.com/EricPraveen/ClassPulse/actions/workflows/ci.yml)
[![Security](https://github.com/EricPraveen/ClassPulse/actions/workflows/security.yml/badge.svg)](https://github.com/EricPraveen/ClassPulse/actions/workflows/security.yml)

**IN3111 – Network Programming | Group Assignment Starter**

ClassPulse is a lightweight Java client–server classroom polling application. A lecturer creates a classroom and publishes a multiple-choice poll. Students join using a classroom code and submit answers. The server collects responses and calculates the results.

This repository is an **assignment-sized starter**, not a completed application. It provides a Maven multi-module foundation, shared message/model types, package boundaries, and team documentation. The networking features are intentionally left for the five team members to implement.

## Current starter scope

- Java 21
- Maven multi-module build: `common`, `server`, and `client`
- Shared message types and simple domain models
- Planned networking demonstrations:
  - Java NIO central server
  - TCP classroom and polling operations
  - UDP heartbeat/status messages
  - UDP multicast announcements
  - Multithreaded request processing
  - JSSE/TLS secure communication

No database, GUI, cloud service, or external dependency is required for the initial assignment scope. A console-based client is sufficient unless the assignment specification says otherwise.

## Repository structure

```text
ClassPulse/
├── pom.xml
├── common/
│   ├── pom.xml
│   └── src/main/java/com/classpulse/common/
│       ├── model/
│       └── protocol/
├── server/
│   ├── pom.xml
│   └── src/main/java/com/classpulse/server/
│       ├── MainServer.java
│       ├── nio/
│       ├── tcp/
│       ├── udp/
│       ├── multicast/
│       ├── concurrency/
│       ├── security/
│       ├── classroom/
│       └── poll/
├── client/
│   ├── pom.xml
│   └── src/main/java/com/classpulse/client/
│       ├── Main.java
│       ├── network/
│       ├── lecturer/
│       └── student/
└── docs/
```

## Prerequisites

- JDK 21
- Apache Maven 3.9.x (or a compatible Maven 3.x release)
- Git

Check the tools:

```bash
java -version
mvn -version
git --version
```

## Build

Run Maven from the **ClassPulse project root**, the directory containing the parent `pom.xml`:

```bash
mvn clean install
```

The parent POM builds all three modules in dependency order. A successful build verifies compilation and module wiring; it does **not** prove that the network features work end-to-end.

## Run

The starter entry points are placeholders. Once the server and client implementations are complete, update the commands below to match the final launch configuration.

Example from the project root:

```bash
mvn -pl server -am package
mvn -pl client -am package
```

Use the final classpath or Maven execution configuration agreed by the team to launch `com.classpulse.server.MainServer` and `com.classpulse.client.Main`.

## Team responsibilities

See [`docs/team-responsibilities.md`](docs/team-responsibilities.md). The proposed split is:

1. Java NIO central server
2. TCP classroom and polling functionality
3. UDP heartbeat and multicast announcements
4. Multithreading and concurrency
5. JSSE/TLS security

Agree on shared APIs and message formats before parallel implementation. All members should work against the same central server and shared `common` module.

## Key documents

- [Project context](docs/project-context.md)
- [Architecture](docs/architecture.md)
- [Communication protocol](docs/communication-protocol.md)
- [Team responsibilities](docs/team-responsibilities.md)
- [Setup guide](docs/setup-guide.md)
- [Roadmap](ROADMAP.md)
- [Task tracker](TASKS.md)
- [Decision log](DECISIONS.md)
- [Contribution guide](CONTRIBUTING.md)

## Task status convention

- `[ ] TODO` – not started
- `[-] IN PROGRESS` – currently being worked on
- `[x] DONE` – implemented and checked
- `[!] BLOCKED` – cannot proceed; explain why

Update `TASKS.md` when work starts or finishes. Mark a task done only after the relevant check has passed.

## License

No license has been selected. Do not assume the project is open source until the group chooses one.
