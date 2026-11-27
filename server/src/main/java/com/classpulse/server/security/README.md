# Server Security (JSSE/TLS) – ClassPulse

This package implements the transport-layer security (TLS) components for the ClassPulse central server using standard Java Secure Socket Extension (JSSE).

---

## Components

- **`TlsConfig`**: Immutable configuration bean holding port, keystore/truststore paths, credentials, and enabled TLS protocols. Supports system properties and environment variables with in-memory password wiping.
- **`SslContextFactory`**: Factory for initializing JSSE `SSLContext` instances using `KeyManagerFactory` and `TrustManagerFactory`. Strictly enforces X.509 certificate validation.
- **`TlsSessionInfo`**: Extracts and encapsulates negotiated TLS protocol versions (TLSv1.3, TLSv1.2), cipher suites (e.g. `TLS_AES_256_GCM_SHA384`), peer certificates, and connection timestamps.
- **`TlsClientConnection`**: Interface representing a connected client session over TLS, providing `send(Message)`, `getSessionInfo()`, and lifecycle management.
- **`TlsMessageHandler`**: Functional interface for dispatching decoded protocol messages to classroom and polling handlers.
- **`TlsServer`**: Multi-threaded TLS server listening on port `5443` (configurable), performing the TLS handshake, logging negotiated cipher suites, and handling length-prefixed `Message` frames.
- **`TlsServerMain`**: Standalone executable console server entry point for live demonstrations.
- **`TlsConfigurationException`**: Runtime exception thrown on keystore loading or TLS initialization failures.

---

## Certificate Generation

Run the generation script from the project root:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate-dev-certs.ps1
```

See [`docs/tls-setup.md`](../../../../../../../docs/tls-setup.md) for full instructions and architecture details.
