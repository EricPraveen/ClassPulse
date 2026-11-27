# JSSE/TLS Secure Communication Setup & Verification Guide

**Project:** ClassPulse (IN3111 Network Programming)  
**Assigned Member:** Nuwanthi (Member 5 – JSSE/TLS Security)  
**Deliverable:** Issue #15 – Implement and verify JSSE/TLS client–server communication  
**Technology:** Java 21, Java JSSE (`javax.net.ssl`), Maven  

---

## 1. Purpose of JSSE/TLS in ClassPulse

ClassPulse is a distributed classroom polling system where lecturers create questions and students submit responses. Plaintext TCP connections expose all application traffic to eavesdropping, packet tampering, and man-in-the-middle (MitM) attacks.

Java Secure Socket Extension (JSSE) provides transport-layer security (TLS) for ClassPulse:
- **Confidentiality:** All application payloads (classroom codes, poll questions, and student votes) are encrypted using strong symmetric ciphers negotiated during the TLS handshake.
- **Integrity:** Message payloads are protected with cryptographic message authentication codes (AEAD ciphers such as AES-GCM) to detect any unauthorized modification in transit.
- **Server Authentication:** Clients cryptographically verify the server's identity using X.509 digital certificates and public-key cryptography, preventing impersonation.
- **Identity & Hostname Verification:** Endpoint identification (`HTTPS` algorithm) validates that the certificate matches the server hostname (`localhost` or IP address `127.0.0.1`).

---

## 2. Architecture & Implementation Approach

### Architectural Principles
1. **Preserve Application Protocol:** The universal protocol framing (4-byte big-endian length prefix followed by UTF-8 JSON `Message` envelopes) defined in `common` remains completely unchanged. JSSE operates beneath the application protocol layer: `ProtocolFramer` reads and writes plaintext bytes through decrypted TLS streams (`SSLSocket.getInputStream()` and `SSLSocket.getOutputStream()`).
2. **Dedicated TLS Endpoint:** To avoid destabilizing Member 1's non-blocking Java NIO selector loop on port 5000, secure communication is provided via `TlsServer` listening on port `5443` (configurable), using `SSLServerSocket` and multithreaded worker request handling.
3. **No Compromise on Security:** Certificate path validation is strictly enforced; trust-all TrustManagers are never used. Server hostname identity verification is active by default.
4. **Zero Hardcoded Secrets:** Private keys and keystore passwords are never stored in source code. Configuration values are resolved from environment variables or system properties with developer fallbacks. All generated keystores, truststores, and certificate files are excluded via `.gitignore`.

### Architecture Diagram

```text
       +---------------------------------------------+
       |             TlsClient (client)              |
       |  - Truststore validation (client-truststore)|
       |  - Hostname verification (RFC 2818 HTTPS)   |
       |  - TLS 1.3 / 1.2 handshake negotiation      |
       +---------------------------------------------+
                              |
                     TLS Encrypted Stream
                   (e.g., TLS_AES_256_GCM)
                              |
       +---------------------------------------------+
       |             TlsServer (server)              |
       |  - SSLServerSocket (port 5443)              |
       |  - Keystore private key (server-keystore)   |
       |  - Handshake event logging & session info   |
       +---------------------------------------------+
                              |
                   Decrypted JSON Message
                              |
       +---------------------------------------------+
       |        Composite Message Handler            |
       |  - ClassroomMessageHandler                  |
       |  - PollMessageHandler                       |
       +---------------------------------------------+
```

---

## 3. Relevant Classes & Package Layout

### Server Module (`server/src/main/java/com/classpulse/server/security`)

| Class | Responsibility |
|---|---|
| `TlsConfig` | Immutable configuration holder with builder, environment variable/property resolution (`CLASSPULSE_TLS_*`), and in-memory password wiping (`wipePasswords()`). |
| `SslContextFactory` | Factory that loads PKCS12 keystores/truststores and initializes JSSE `SSLContext` instances with `KeyManagerFactory` and `TrustManagerFactory`. |
| `TlsSessionInfo` | Extracts and encapsulates negotiated TLS version, cipher suite, peer certificate principal, local principal, and session creation timestamp. |
| `TlsClientConnection` | Interface representing a connected client session over TLS, providing `send(Message)`, `getSessionInfo()`, and lifecycle management. |
| `TlsMessageHandler` | Functional interface for dispatching decoded protocol messages to classroom and polling handlers. |
| `TlsServer` | Multi-threaded TLS server managing `SSLServerSocket`, TLS handshake logging, client thread pool execution, and socket cleanup. |
| `TlsServerMain` | Executable console entry point to run the standalone ClassPulse secure server. |
| `TlsConfigurationException` | Runtime exception indicating missing keystores, bad passwords, or cryptographic initialization failures. |

### Client Module (`client/src/main/java/com/classpulse/client/network`)

| Class | Responsibility |
|---|---|
| `TlsClient` | AutoCloseable TLS client transport managing `SSLSocket`, certificate validation, endpoint identification algorithm (`HTTPS`), TLS handshake, and framed request–response exchanges via `ProtocolFramer`. |
| `TlsSessionInfo` | Client-side representation of the negotiated TLS protocol, cipher suite, and server identity. |
| `TlsClientMain` | Executable console demonstration client that connects securely, performs TLS handshake, creates a classroom, joins as a student, creates a poll, and submits an answer. |

### Shared Protocol Integration (`common`)
- `com.classpulse.common.protocol.Message`
- `com.classpulse.common.protocol.MessageType`
- `com.classpulse.common.protocol.ProtocolFramer`

---

## 4. Certificate, Keystore & Truststore Setup

ClassPulse uses standard **PKCS12** (`.p12`) keystores and truststores.

### Automated Setup Script
A helper script is provided in `scripts/`:

**PowerShell (Windows):**
```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate-dev-certs.ps1
```

**Bash (Linux / macOS):**
```bash
chmod +x scripts/generate-dev-certs.sh
./scripts/generate-dev-certs.sh
```

### Manual Setup via Java `keytool`

If generating manually from the project root:

1. **Create the `certs/` directory:**
   ```bash
   mkdir -p certs
   ```

2. **Generate the Server Key Pair and PKCS12 Keystore:**
   ```bash
   keytool -genkeypair \
     -alias classpulse-server \
     -keyalg RSA \
     -keysize 2048 \
     -validity 365 \
     -keystore certs/server-keystore.p12 \
     -storetype PKCS12 \
     -storepass changeit \
     -keypass changeit \
     -dname "CN=localhost, OU=ClassPulse, O=University, L=Colombo, C=LK" \
     -ext "SAN=dns:localhost,ip:127.0.0.1"
   ```

3. **Export the Server's Public Certificate:**
   ```bash
   keytool -exportcert \
     -alias classpulse-server \
     -keystore certs/server-keystore.p12 \
     -storepass changeit \
     -file certs/server-cert.crt \
     -rfc
   ```

4. **Import the Certificate into the Client Truststore:**
   ```bash
   keytool -importcert \
     -alias classpulse-server \
     -file certs/server-cert.crt \
     -keystore certs/client-truststore.p12 \
     -storetype PKCS12 \
     -storepass changeit \
     -noprompt
   ```

5. **Clean up intermediate certificate file:**
   ```bash
   rm -f certs/server-cert.crt
   ```

---

## 5. Configuration Options

| Option | System Property | Environment Variable | Default Value | Description |
|---|---|---|---|---|
| Server Port | `classpulse.tls.port` | `CLASSPULSE_TLS_PORT` | `5443` | Listening port for TLS server |
| Keystore Path | `classpulse.tls.keystore.path` | `CLASSPULSE_TLS_KEYSTORE_PATH` | `certs/server-keystore.p12` | Path to server PKCS12 keystore |
| Keystore Password | `classpulse.tls.keystore.password` | `CLASSPULSE_TLS_KEYSTORE_PASSWORD` | `changeit` | Password for keystore |
| Keystore Type | `classpulse.tls.keystore.type` | `CLASSPULSE_TLS_KEYSTORE_TYPE` | `PKCS12` | Format of keystore (`PKCS12` or `JKS`) |
| Truststore Path | `classpulse.tls.truststore.path` | `CLASSPULSE_TLS_TRUSTSTORE_PATH` | `certs/client-truststore.p12` | Path to client PKCS12 truststore |
| Truststore Password | `classpulse.tls.truststore.password` | `CLASSPULSE_TLS_TRUSTSTORE_PASSWORD` | `changeit` | Password for client truststore |
| Enabled Protocols | — | — | `TLSv1.3, TLSv1.2` | Enabled TLS protocol versions |

---

## 6. How to Run the Demonstration

### Step 1: Build the Project
From the repository root:
```bash
mvn clean package -DskipTests
```

### Step 2: Start the Secure TLS Server
In Terminal 1:
```bash
# Using standard Java classpath
java -cp "server/target/server-1.0-SNAPSHOT.jar;common/target/common-1.0-SNAPSHOT.jar;C:/Users/ASUS VIVOBOOK/.m2/repository/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar" com.classpulse.server.security.TlsServerMain 5443
```
*Expected Server Output:*
```text
=================================================
     ClassPulse Secure TLS Server (IN3111)       
=================================================
[TLS Server] Secure server listening on port 5443 (Protocols: TLSv1.3, TLSv1.2)
[INFO] ClassPulse TLS Server is ready on port 5443.
[INFO] Press Ctrl+C to terminate.
```

### Step 3: Run the Secure TLS Client
In Terminal 2:
```bash
java -cp "client/target/client-1.0-SNAPSHOT.jar;common/target/common-1.0-SNAPSHOT.jar;C:/Users/ASUS VIVOBOOK/.m2/repository/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar" com.classpulse.client.network.TlsClientMain localhost 5443
```
*Expected Client Output:*
```text
=================================================
     ClassPulse Secure TLS Client (IN3111)       
=================================================
[INFO] Connecting to TLS server at localhost:5443 using truststore certs/client-truststore.p12...
[TLS Client] Connected securely to localhost:5443 [TLS TLSv1.3 | Cipher: TLS_AES_256_GCM_SHA384 | Server: CN=localhost,OU=ClassPulse,O=University,L=Colombo,C=LK]

-------------------------------------------------
TLS Handshake Succeeded!
  Protocol:     TLSv1.3
  Cipher Suite: TLS_AES_256_GCM_SHA384
  Server Cert:  CN=localhost,OU=ClassPulse,O=University,L=Colombo,C=LK
-------------------------------------------------

[Step 1] Creating classroom 'NET101' over TLS...
  Response Type: CLASSROOM_CREATED
  Classroom Code: QL8PUP

[Step 2] Student joining classroom over TLS...
  Response Type: CLASSROOM_JOINED
  Student ID:     student-f24341c6

[Step 3] Lecturer publishing poll over TLS...
  Response Type: POLL_CREATED
  Poll ID:       poll-501d8785

[Step 4] Student submitting answer over TLS...
  Response Type: ANSWER_SUBMITTED
  Answer Status: ACCEPTED

[SUCCESS] All secure message exchanges completed successfully!
```

---

## 7. Automated Test Suite Results

The automated test suite verifies all nine acceptance scenarios:

| # | Scenario | Verified By | Result |
|---|---|---|---|
| 1 | TLS server starts successfully on configured/ephemeral port | `TlsServerTest.testTlsServerLifecycle` | **PASSED** |
| 2 | TLS client connects successfully | `TlsClientTest.testSuccessfulTlsConnectAndSessionInfo` | **PASSED** |
| 3 | TLS handshake completes successfully | `TlsClientTest.testSuccessfulTlsConnectAndSessionInfo` | **PASSED** |
| 4 | Client and server exchange framed messages securely | `TlsClientTest.testSecureRequestResponseExchange` | **PASSED** |
| 5 | Negotiated TLS version and cipher suite displayed | `TlsClientTest.testSuccessfulTlsConnectAndSessionInfo`, `TlsServerTest.testTlsSessionInfoDisplay` | **PASSED** |
| 6 | Untrusted server certificate rejected by client truststore | `TlsClientTest.testUntrustedServerCertificateRejected` | **PASSED** |
| 7 | Incorrect server identity (hostname mismatch) rejected | `TlsClientTest.testIncorrectServerIdentityRejected` | **PASSED** |
| 8 | Sockets and streams close correctly on disconnect | `TlsClientTest.testConnectionCloseAndResources`, `TlsServerTest.testTlsServerLifecycle` | **PASSED** |
| 9 | Existing project functionality and regression suite | Full reactor `mvn clean install` | **PASSED** (155/155 tests) |

### Test Execution Summary
```text
[INFO] Reactor Summary for classpulse 1.0-SNAPSHOT:
[INFO] 
[INFO] classpulse ......................................... SUCCESS
[INFO] ClassPulse Common .................................. SUCCESS (19 tests)
[INFO] ClassPulse Server .................................. SUCCESS (67 tests)
[INFO] ClassPulse Client .................................. SUCCESS (69 tests)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS (Total 155 tests, 0 failures, 0 errors, 0 skipped)
```

---

## 8. Known Limitations & Integration Notes

1. **Self-Signed Certificates for Development:** The development certificates use self-signed RSA key pairs. In production, certificates would be issued by a trusted Public Certificate Authority (CA) or campus PKI.
2. **NIO Non-Blocking Server Integration:** The team architecture maintains Member 1's `NioServer` on port `5000` for selector-based TCP demonstration, while `TlsServer` operates on port `5443` for JSSE/TLS demonstration. If future assignment requirements mandate embedding TLS directly into non-blocking NIO channels, `SSLEngine` state machine unwrapping/wrapping can be added without changing the shared `Message` protocol.
3. **Mutual TLS (mTLS):** Client certificate authentication is supported by `TlsConfig.clientAuthRequired(true)`, but default demo operates in standard one-way TLS (server authentication), which is sufficient for classroom polling applications.
