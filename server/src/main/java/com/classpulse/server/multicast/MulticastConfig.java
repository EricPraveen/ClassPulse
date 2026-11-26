package com.classpulse.server.multicast;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * Shared configuration defaults and validation helpers for ClassPulse
 * UDP multicast classroom announcements.
 */
public final class MulticastConfig {

    /** Administratively scoped IPv4 multicast group (RFC 2365). */
    public static final String DEFAULT_GROUP_ADDRESS = "239.255.42.99";

    /** Default UDP multicast port (separate from TCP 5000 and UDP heartbeat 5001). */
    public static final int DEFAULT_PORT = 5002;

    /** Default multicast Time-To-Live (1 = local subnet only). */
    public static final int DEFAULT_TTL = 1;

    /** Maximum UTF-8 JSON announcement datagram payload size in bytes. */
    public static final int MAX_DATAGRAM_BYTES = 2048;

    private MulticastConfig() {
        // Utility class
    }

    /**
     * Resolves and validates that the supplied address is a valid multicast address.
     */
    public static InetAddress resolveGroupAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Multicast group address must not be null or blank");
        }
        try {
            InetAddress resolved = InetAddress.getByName(address.trim());
            if (!resolved.isMulticastAddress()) {
                throw new IllegalArgumentException("Address is not a multicast address: " + address);
            }
            return resolved;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve multicast group address: " + address, e);
        }
    }

    /**
     * Validates that the UDP port is in the range 1..65535.
     */
    public static int validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535: " + port);
        }
        return port;
    }

    /**
     * Validates that the multicast TTL is in the range 0..255.
     */
    public static int validateTtl(int ttl) {
        if (ttl < 0 || ttl > 255) {
            throw new IllegalArgumentException("Multicast TTL must be between 0 and 255: " + ttl);
        }
        return ttl;
    }

    /**
     * Resolves an IPv4 multicast-capable network interface.
     * <p>
     * If {@code interfaceName} is specified, that exact interface is verified.
     * Otherwise, an active IPv4 multicast-capable interface is selected automatically,
     * preferring non-loopback interfaces and falling back to loopback if available.
     *
     * @param interfaceName optional OS interface name (e.g. {@code eth0}, {@code wlan0}, {@code lo})
     * @return resolved {@link NetworkInterface}
     * @throws IOException if no suitable multicast interface is available
     */
    public static NetworkInterface resolveNetworkInterface(String interfaceName) throws IOException {
        if (interfaceName != null && !interfaceName.trim().isEmpty()) {
            String trimmed = interfaceName.trim();
            NetworkInterface explicit = NetworkInterface.getByName(trimmed);
            if (explicit == null) {
                throw new IOException("Multicast network interface not found: " + trimmed);
            }
            if (!explicit.isUp()) {
                throw new IOException("Multicast network interface is down: " + trimmed);
            }
            if (!explicit.supportsMulticast()) {
                throw new IOException("Network interface does not support multicast: " + trimmed);
            }
            return explicit;
        }

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        NetworkInterface loopbackFallback = null;

        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (!isUsableIpv4MulticastInterface(ni)) {
                continue;
            }
            if (!ni.isLoopback()) {
                return ni;
            }
            if (loopbackFallback == null) {
                loopbackFallback = ni;
            }
        }

        if (loopbackFallback != null) {
            return loopbackFallback;
        }
        throw new IOException("No active IPv4 multicast-capable network interface is available");
    }

    private static boolean isUsableIpv4MulticastInterface(NetworkInterface ni) throws SocketException {
        if (!ni.isUp() || !ni.supportsMulticast()) {
            return false;
        }
        Enumeration<InetAddress> addresses = ni.getInetAddresses();
        while (addresses.hasMoreElements()) {
            if (addresses.nextElement() instanceof Inet4Address) {
                return true;
            }
        }
        return false;
    }
}
