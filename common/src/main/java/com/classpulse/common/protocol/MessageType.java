package com.classpulse.common.protocol;

/**
 * Message categories shared by the ClassPulse client and server.
 * Extend this enum only after agreeing on protocol changes as a team.
 */
public enum MessageType {
    CREATE_CLASSROOM,
    JOIN_CLASSROOM,
    CREATE_POLL,
    SUBMIT_ANSWER,
    POLL_RESULTS,
    ANNOUNCEMENT,
    HEARTBEAT,
    ERROR
}
