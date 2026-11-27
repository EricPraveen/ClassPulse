# Roadmap – ClassPulse

This roadmap is intentionally small and aligned with the five-member assignment split.

## Phase 0 – Foundation

- [x] Create Maven parent project and `common`, `server`, `client` modules.
- [x] Add shared starter models and protocol types.
- [x] Add project documentation and team responsibility boundaries.
- [ ] Confirm the official assignment requirements and team members.
- [ ] Agree on ports, message framing, and protocol payloads.

**Milestone:** every member can clone the repository and run `mvn clean install`.

## Phase 1 – Core client/server path

- [ ] Implement the Java NIO central server.
- [ ] Implement a basic client connection.
- [ ] Implement classroom creation and joining.
- [ ] Implement poll creation and answer submission.
- [ ] Calculate and return poll results.

**Milestone:** lecturer and at least two student clients complete a poll workflow.

## Phase 2 – Required networking demonstrations

- [ ] Add UDP heartbeat/status messages.
- [ ] Add multicast announcements.
- [x] Add concurrent request processing and safe shared-state updates.
- [ ] Add JSSE/TLS connection support.

**Milestone:** each assigned networking concept has a reproducible demonstration.

## Phase 3 – Integration and verification

- [ ] Integrate all member branches.
- [ ] Test multiple simultaneous clients.
- [ ] Test invalid input and disconnect handling.
- [ ] Verify UDP/multicast behaviour on the demonstration network.
- [ ] Verify TLS handshake and secure client/server communication.
- [ ] Update setup instructions and architecture documentation.

**Milestone:** a clean checkout builds and the agreed end-to-end demo works.

## Phase 4 – Submission

- [ ] Prepare final report based on implemented features.
- [ ] Capture evidence/screenshots or packet captures if required.
- [ ] Rehearse the group demonstration.
- [ ] Tag or identify the final submission commit.

**Milestone:** source code, report, and demo are ready for submission.
