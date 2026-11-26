package com.classpulse.common.protocol;

/**
 * Message types shared across the ClassPulse client and server.
 * Defines the operations supported by the shared TCP communication protocol.
 */
public enum MessageType {
    // Classroom operations
    CREATE_CLASSROOM,
    CLASSROOM_CREATED,
    JOIN_CLASSROOM,
    CLASSROOM_JOINED,

    // Polling operations
    CREATE_POLL,
    POLL_CREATED,
    GET_POLL,
    SUBMIT_ANSWER,
    ANSWER_SUBMITTED,
    POLL_RESULTS,

    // General control and status
    ERROR,
    SUCCESS,
    DISCONNECT,

    // Supplementary networking concepts
    ANNOUNCEMENT,
    HEARTBEAT
}
