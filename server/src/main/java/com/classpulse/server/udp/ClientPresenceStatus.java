package com.classpulse.server.udp;

/**
 * Presence status of a client tracked via UDP heartbeats.
 */
public enum ClientPresenceStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN
}
