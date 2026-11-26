# Tasks – ClassPulse

Statuses: `[ ] TODO`, `[-] IN PROGRESS`, `[x] DONE`, `[!] BLOCKED`.

Only mark a task `[x] DONE` after its acceptance check has passed. Add new tasks under the relevant phase and keep `ROADMAP.md` in sync when milestones change.

## P0 – Foundation and team agreement

- [x] P0-01 Create Maven parent and three modules.
- [x] P0-02 Add shared starter model and protocol classes.
- [x] P0-03 Add project context, architecture, and team documentation.
- [ ] P0-04 Confirm assignment requirements against the official brief.
- [x] P0-05 Agree on TCP framing, message payloads, and port numbers.
- [ ] P0-06 Assign the five roles and record names in `docs/team-responsibilities.md`.

## P1 – Core application

- [ ] P1-01 Implement NIO server accept/read/write lifecycle.
- [x] P1-02 Implement client connection and message framing.
- [x] P1-03 Implement lecturer classroom creation.
- [x] P1-04 Implement student classroom joining.
- [ ] P1-05 Implement poll creation and validation.
- [ ] P1-06 Implement answer submission and result calculation.
- [ ] P1-07 Demonstrate at least two student clients connected concurrently.

## P2 – Networking concepts

- [ ] P2-01 Implement UDP heartbeat/status sender and receiver.
- [ ] P2-02 Implement multicast announcement sender and receiver.
- [ ] P2-03 Implement worker-thread request processing.
- [ ] P2-04 Protect shared poll response state from race conditions.
- [ ] P2-05 Configure JSSE/TLS on the agreed client-server connection.
- [ ] P2-06 Verify a successful TLS handshake.

## P3 – Integration and testing

- [ ] P3-01 Integrate member work into the shared development branch.
- [ ] P3-02 Test invalid classroom codes and invalid poll answers.
- [ ] P3-03 Test client disconnect and server shutdown.
- [ ] P3-04 Test simultaneous poll submissions and result totals.
- [ ] P3-05 Test UDP and multicast on the target network.
- [ ] P3-06 Run `mvn clean install` from a fresh checkout.
- [ ] P3-07 Update README and setup guide with final run commands.

## P4 – Submission

- [ ] P4-01 Update report to describe only completed features.
- [ ] P4-02 Add actual test results and demonstration evidence.
- [ ] P4-03 Rehearse the group demonstration.
- [ ] P4-04 Identify the final submission commit.

## Task update rules

1. Use a unique ID such as `P2-07`.
2. Add a short action and a clear acceptance check.
3. Change status to `[-]` when work starts.
4. Change status to `[x]` only after implementation and verification.
5. If blocked, use `[!]` and write the reason.
6. Keep the roadmap milestone checkboxes consistent with completed tasks.
