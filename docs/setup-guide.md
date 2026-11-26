# Setup Guide

## Requirements

- JDK 21
- Maven 3.x
- Git

## Verify installation

```bash
java -version
mvn -version
git --version
```

## Open the correct folder

Open the `ClassPulse/` folder itself in your IDE. It contains the parent `pom.xml`. If the IDE does not recognise the modules, reload Maven projects and refresh the Java language server.

## Build

Run from the directory containing the root `pom.xml`:

```bash
mvn clean install
```

Do not run Maven from the folder above `ClassPulse`, because that directory does not contain the project POM.

## Windows PowerShell example

```powershell
cd "D:\path\to\ClassPulse"
mvn clean install
```

## macOS / Linux example

```bash
cd /path/to/ClassPulse
mvn clean install
```

## IDE notes

- Set the project JDK to Java 21.
- Import/open the root `pom.xml` as a Maven project.
- If red diagnostics remain, inspect the actual Problems panel messages rather than assuming they are harmless.
- A successful Maven build confirms compilation; it does not validate runtime networking behaviour.

## Run configuration

### 1. Central Server (`server` module)
Run `MainServer` to start the central Java NIO server on port 5000:
```bash
java -cp server/target/server-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar com.classpulse.server.MainServer [optional-port]
```

### 2. Lecturer Console (`client` module)
Run the lecturer interactive console to create classrooms, publish polls, and view results:
```bash
java -cp client/target/client-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar com.classpulse.client.lecturer.LecturerMain [host] [port]
```
Or launch via the client role selector:
```bash
java -jar client/target/client-1.0-SNAPSHOT.jar --lecturer
```

### 3. Student Console (`client` module)
Run the student interactive console to join classrooms, view polls, and submit answers:
```bash
java -cp client/target/client-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar com.classpulse.client.student.StudentMain [host] [port]
```
Or launch via the client role selector:
```bash
java -jar client/target/client-1.0-SNAPSHOT.jar --student
```

### 4. UDP Multicast Classroom Announcements Demo (`server` & `client` modules)

The UDP multicast demonstration broadcasts announcements one-to-many on group **`239.255.42.99`**, **UDP port `5002`**, **TTL `1`**. Ensure `gson` is on the classpath when running standalone classes.

#### Start two or more multicast receivers
Windows PowerShell (use `;` as classpath separator):
```powershell
$gson = "$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.11.0\gson-2.11.0.jar"
java -cp "client\target\client-1.0-SNAPSHOT.jar;common\target\common-1.0-SNAPSHOT.jar;$gson" com.classpulse.client.network.MulticastAnnouncementReceiverMain 239.255.42.99 5002
### 4. UDP Heartbeat & Client Status Demo (`server` & `client` modules)

The UDP heartbeat demonstration runs independently of the TCP NIO server on **UDP port `5001`** (default interval `5s`, offline timeout `15s`). Ensure `gson` is on the classpath when running standalone classes.

#### Start the UDP heartbeat receiver
Windows PowerShell (use `;` as classpath separator):
```powershell
$gson = "$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.11.0\gson-2.11.0.jar"
java -cp "server\target\server-1.0-SNAPSHOT.jar;common\target\common-1.0-SNAPSHOT.jar;$gson" com.classpulse.server.udp.UdpHeartbeatServerMain 5001 15
```
macOS / Linux (use `:` as classpath separator):
```bash
GSON="$HOME/.m2/repository/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar"
java -cp "client/target/client-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar:$GSON" com.classpulse.client.network.MulticastAnnouncementReceiverMain 239.255.42.99 5002
```
*(Optional 3rd argument specifies a network interface name, e.g. `eth0` or `wlan0`.)*

#### Start the multicast sender
Windows PowerShell:
```powershell
$gson = "$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.11.0\gson-2.11.0.jar"
java -cp "server\target\server-1.0-SNAPSHOT.jar;common\target\common-1.0-SNAPSHOT.jar;$gson" com.classpulse.server.multicast.MulticastAnnouncementSenderMain 239.255.42.99 5002 1
java -cp "server/target/server-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar:$GSON" com.classpulse.server.udp.UdpHeartbeatServerMain 5001 15
```

#### Start one or more UDP heartbeat clients
Windows PowerShell:
```powershell
$gson = "$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.11.0\gson-2.11.0.jar"
java -cp "client\target\client-1.0-SNAPSHOT.jar;common\target\common-1.0-SNAPSHOT.jar;$gson" com.classpulse.client.network.UdpHeartbeatClientMain student-1 127.0.0.1 5001 5
```
macOS / Linux:
```bash
GSON="$HOME/.m2/repository/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar"
java -cp "server/target/server-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar:$GSON" com.classpulse.server.multicast.MulticastAnnouncementSenderMain 239.255.42.99 5002 1
```
*(Optional 4th argument specifies the outgoing network interface name.)*

#### Manual test procedure (2+ receivers)
1. Open two separate terminals and start `MulticastAnnouncementReceiverMain` in each. Both bind with `SO_REUSEADDR` and join `239.255.42.99:5002`.
2. Open a third terminal and start `MulticastAnnouncementSenderMain`.
3. Enter a classroom code (e.g. `CS101`), sender name, and type an announcement line.
4. Confirm that **both** receiver terminals immediately display the announcement.
5. Type `exit` in the sender and press Enter in each receiver to verify clean shutdown and group leave.

#### Network, interface, and firewall caveats
- **Interface selection:** By default, sender and receiver automatically pick an active IPv4 multicast-capable interface (preferring non-loopback, falling back to loopback). On multi-homed hosts (VPN/Docker/virtual adapters), pass the explicit interface name if packets go out the wrong adapter.
- **Campus / guest Wi-Fi:** Many campus or public Wi-Fi networks block or isolate IP multicast traffic (client isolation / IGMP filtering). If multicast fails across machines on campus Wi-Fi, test on the same host (loopback) or on a wired lab subnet / private hotspot.
- **Firewall:** Allow inbound/outbound **UDP port `5002`** and multicast traffic (`239.255.42.99`) in Windows Defender Firewall or `ufw`.
java -cp "client/target/client-1.0-SNAPSHOT.jar:common/target/common-1.0-SNAPSHOT.jar:$GSON" com.classpulse.client.network.UdpHeartbeatClientMain student-1 127.0.0.1 5001 5
```

#### Manual test procedure
1. Start `UdpHeartbeatServerMain` on port `5001` with a `15`s timeout.
2. Start `UdpHeartbeatClientMain` with client ID `student-1`.
3. Observe `[UDP HEARTBEAT]` logs and status printout showing `student-1 ONLINE`.
4. Stop the client (press Enter or `Ctrl+C`) and wait `15` seconds without restarting it.
5. Observe the status table transition `student-1` from `ONLINE` to `OFFLINE`.

#### Local-network and firewall notes
- **Loopback vs. LAN:** Use `127.0.0.1` on the same machine, or pass the server's LAN IPv4 address when running across two machines.
- **Firewall:** Allow inbound **UDP port `5001`** on the server host (Windows Defender Firewall or `ufw allow 5001/udp` on Linux) when testing across separate machines.
- **Unreliable transport:** UDP does not guarantee delivery or order; a single dropped datagram is tolerated because heartbeats repeat every `5s` before the `15s` timeout expires.
