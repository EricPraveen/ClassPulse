# Client Network Transport – ClassPulse

This package provides the reusable TCP client connection and request–response transport for ClassPulse.

Lecturer and student workflows use this networking layer to communicate with the central server without managing raw sockets or byte streams directly.

---

## Architecture Overview

- **`TcpClient`**: Main client class implementing `AutoCloseable`. Handles TCP connection lifecycle, socket timeouts, and request-response messaging.
- **`ClientTransportException`**: Custom `IOException` subclass providing descriptive error messages for connection failures, timeouts, unexpected disconnects, and protocol errors.
- **Protocol Framing**: Utilizes `com.classpulse.common.protocol.ProtocolFramer` for 4-byte big-endian length prefix encoding and UTF-8 JSON envelopes.

---

## Usage Example

### Basic Request–Response Exchange

```java
import com.classpulse.client.network.TcpClient;
import com.classpulse.client.network.ClientTransportException;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;

public class Example {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5000;

        // Try-with-resources guarantees sockets and streams are released on exit
        try (TcpClient client = new TcpClient(host, port)) {
            client.connect();

            // Construct structured request
            Message request = new Message(MessageType.JOIN_CLASSROOM, "req-001");
            request.putPayload("classroomCode", "CS101");
            request.putPayload("studentName", "Alice");

            // Send request and wait for correlated response
            Message response = client.sendRequest(request);

            if (response.getType() == MessageType.CLASSROOM_JOINED) {
                System.out.println("Joined room: " + response.getPayloadString("classroomCode"));
            } else if (response.getType() == MessageType.ERROR) {
                System.err.println("Server error: " + response.getPayloadString("message"));
            }
        } catch (ClientTransportException e) {
            System.err.println("Network communication error: " + e.getMessage());
        }
    }
}
```

---

## Configuration

| Parameter | Default Value | Description |
|---|---|---|
| `host` | *Required* | Server hostname or IP address (e.g. `127.0.0.1` or `localhost`). |
| `port` | *Required* | Server TCP port (1–65535, e.g. `5000`). |
| `connectTimeoutMillis` | `5000` (5s) | Timeout when attempting to establish TCP connection. |
| `readTimeoutMillis` | `10000` (10s) | Socket read timeout (`SO_TIMEOUT`) when waiting for responses. |

Constructors:
- `TcpClient(String host, int port)`
- `TcpClient(String host, int port, int connectTimeoutMillis, int readTimeoutMillis)`

---

## Error Handling

All transport errors are wrapped in `ClientTransportException`:

- **Connection Refused / Server Unavailable**: Reported when the server is down or the port is inaccessible.
- **Connection Timeout**: Reported if the server does not respond within the connection timeout.
- **Read Timeout**: Reported if the server does not return a response within `readTimeoutMillis`.
- **Unexpected Disconnection / EOF**: Detected if the server terminates the socket while waiting for data.
- **Malformed Response**: Reported if invalid or corrupt frames are received from the network.
- **Operation when Disconnected**: Calling `send()`, `receive()`, or `sendRequest()` when disconnected throws `ClientTransportException`.
