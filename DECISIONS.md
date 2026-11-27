# Decision Log – ClassPulse

Record decisions that affect multiple members. Add newest decisions at the top.

## D-008 – JSSE/TLS secure communication configuration and certificate management

- **Status:** Accepted
- **Decision:** Use standard Java JSSE (`SSLContext`, `SSLServerSocket`, `SSLSocket`) on dedicated TCP port `5443` for secure communication, PKCS12 keystore format, TLS 1.3 and TLS 1.2 protocols, 2048-bit RSA self-signed development certificates with Subject Alternative Name (`SAN=dns:localhost,ip:127.0.0.1`), strict certificate path validation via `TrustManagerFactory`, and standard endpoint identification algorithm (`HTTPS`) for server hostname verification. Shared `Message` envelopes and length-prefixed framing from `common` are preserved without alteration over the decrypted TLS stream.
- **Reason:** Keeping the TLS endpoint on port `5443` avoids destabilizing the non-blocking Java NIO selector loop on port `5000` while demonstrating full transport confidentiality, message integrity, and authenticated handshakes. PKCS12 is the Java 9+ standard keystore format, and RFC 2818 endpoint identification prevents hostname spoofing.
- **Impact:** Implemented in `com.classpulse.server.security` (`TlsServer`, `TlsConfig`, `SslContextFactory`, `TlsServerMain`) and `com.classpulse.client.network` (`TlsClient`, `TlsClientMain`). Documentation provided in `docs/tls-setup.md`.

## D-007 – UDP multicast announcement configuration

- **Status:** Accepted
- **Decision:** Use administratively scoped IPv4 multicast group `239.255.42.99`, UDP port `5002`, TTL `1` (local subnet only), automatic IPv4 multicast-capable `NetworkInterface` selection (preferring non-loopback, falling back to loopback, with optional explicit interface override), and raw UTF-8 JSON `Message` envelopes with `MessageType.ANNOUNCEMENT` (max `2048` bytes).
- **Reason:** RFC 2365 `239.255.0.0/16` addresses are reserved for local/organization-scoped multicast, and TTL `1` prevents announcements from leaving the local subnet. Port `5002` keeps multicast independent of TCP (`5000`) and UDP heartbeat (`5001`).
- **Impact:** Implemented in `com.classpulse.server.multicast` and `com.classpulse.client.network.MulticastAnnouncementReceiver`.
## D-006 – UDP heartbeat configuration and datagram format

- **Status:** Accepted
- **Decision:** Use UDP port `5001`, raw UTF-8 JSON `Message` envelopes with `MessageType.HEARTBEAT` (without the 4-byte TCP length prefix), a default client heartbeat interval of `5` seconds (`5000 ms`), a server offline timeout of `15` seconds (`15000 ms`), and a maximum datagram size of `2048` bytes.
- **Reason:** UDP preserves datagram boundaries per packet, so length-prefix framing is unnecessary. Keeping UDP heartbeat separate from TCP port `5000` avoids interfering with the NIO classroom/polling workflow while demonstrating independent UDP presence tracking.
- **Impact:** Implemented in `com.classpulse.server.udp` and `com.classpulse.client.network.UdpHeartbeatClient`.

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

- Branching and pull request workflow.

When changing an accepted decision, add a new entry explaining why; do not silently rewrite project history.
