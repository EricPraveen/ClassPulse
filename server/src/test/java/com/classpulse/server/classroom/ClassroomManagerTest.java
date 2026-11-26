package com.classpulse.server.classroom;

import com.classpulse.common.model.Classroom;
import com.classpulse.common.model.User;
import com.classpulse.common.model.UserRole;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClassroomManagerTest {

    private ClassroomManager manager;
    private ClassroomMessageHandler handler;

    @BeforeEach
    void setUp() {
        manager = new ClassroomManager();
        handler = new ClassroomMessageHandler(manager);
    }

    @Test
    @DisplayName("1. A lecturer can create a classroom")
    void testLecturerCanCreateClassroom() {
        Classroom classroom = manager.createClassroom("Prof. Davis");

        assertNotNull(classroom);
        assertNotNull(classroom.getCode());
        assertFalse(classroom.getCode().isBlank());
        assertNotNull(classroom.getLecturerId());
        assertTrue(classroom.getLecturerId().startsWith("lecturer-"));
        assertEquals(1, manager.getActiveClassroomCount());
    }

    @Test
    @DisplayName("2. Generated classroom join codes are unique")
    void testUniqueClassroomCodes() {
        Set<String> codes = new HashSet<>();
        int count = 50;

        for (int i = 0; i < count; i++) {
            Classroom c = manager.createClassroom("Lecturer " + i);
            assertTrue(codes.add(c.getCode()), "Generated duplicate code: " + c.getCode());
        }

        assertEquals(count, manager.getActiveClassroomCount());
    }

    @Test
    @DisplayName("3. A classroom can be found using its identifier (case-insensitive)")
    void testFindClassroomByIdentifier() {
        Classroom created = manager.createClassroom("Dr. Jones", "BIO101");

        Optional<Classroom> found = manager.findClassroom("BIO101");
        assertTrue(found.isPresent());
        assertEquals(created, found.get());

        // Case-insensitivity check
        Optional<Classroom> foundLower = manager.findClassroom("bio101");
        assertTrue(foundLower.isPresent());
        assertEquals(created, foundLower.get());

        assertTrue(manager.hasClassroom("BIO101"));
        assertFalse(manager.hasClassroom("NON_EXISTENT"));
    }

    @Test
    @DisplayName("4. A student can successfully join an existing classroom")
    void testStudentCanJoinClassroom() {
        Classroom classroom = manager.createClassroom("Prof. Turing", "MATH01");

        JoinResult result = manager.joinClassroom("MATH01", "Alice");

        assertTrue(result.isSuccess());
        assertNotNull(result.getStudent());
        assertEquals("Alice", result.getStudent().getDisplayName());
        assertEquals(UserRole.STUDENT, result.getStudent().getRole());
        assertNotNull(result.getStudent().getId());
        assertEquals(classroom, result.getClassroom());
        assertEquals(1, classroom.getStudents().size());
    }

    @Test
    @DisplayName("5. Multiple students can join the same classroom")
    void testMultipleStudentsCanJoin() {
        manager.createClassroom("Prof. Turing", "MATH02");

        JoinResult r1 = manager.joinClassroom("MATH02", "Alice");
        JoinResult r2 = manager.joinClassroom("MATH02", "Bob");
        JoinResult r3 = manager.joinClassroom("MATH02", "Charlie");

        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
        assertTrue(r3.isSuccess());

        List<User> students = manager.getStudents("MATH02");
        assertEquals(3, students.size());
        assertEquals("Alice", students.get(0).getDisplayName());
        assertEquals("Bob", students.get(1).getDisplayName());
        assertEquals("Charlie", students.get(2).getDisplayName());
    }

    @Test
    @DisplayName("6. A student cannot join the same classroom twice (duplicate prevention)")
    void testDuplicateStudentJoiningPrevented() {
        manager.createClassroom("Prof. Turing", "MATH03");

        JoinResult firstJoin = manager.joinClassroom("MATH03", "Alice");
        assertTrue(firstJoin.isSuccess());

        // Attempting to join with the exact same name
        JoinResult duplicateJoin = manager.joinClassroom("MATH03", "Alice");
        assertFalse(duplicateJoin.isSuccess());
        assertEquals("DUPLICATE_STUDENT", duplicateJoin.getErrorCode());
        assertTrue(duplicateJoin.getErrorMessage().contains("already joined"));

        // Attempting with different casing or leading/trailing whitespace
        JoinResult caseDuplicate = manager.joinClassroom("MATH03", "  alice  ");
        assertFalse(caseDuplicate.isSuccess());
        assertEquals("DUPLICATE_STUDENT", caseDuplicate.getErrorCode());

        assertEquals(1, manager.getStudents("MATH03").size());
    }

    @Test
    @DisplayName("7. A student cannot join a classroom that does not exist")
    void testJoinNonExistentClassroom() {
        JoinResult result = manager.joinClassroom("FAKE99", "David");

        assertFalse(result.isSuccess());
        assertEquals("INVALID_CLASSROOM", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("8. Invalid input is handled appropriately without crashing")
    void testInvalidInputs() {
        // Creation validation
        assertThrows(IllegalArgumentException.class, () -> manager.createClassroom(null));
        assertThrows(IllegalArgumentException.class, () -> manager.createClassroom("   "));
        assertThrows(IllegalArgumentException.class, () -> manager.createClassroom("Prof", null));
        assertThrows(IllegalArgumentException.class, () -> manager.createClassroom("Prof", "   "));

        // Joining validation
        JoinResult r1 = manager.joinClassroom(null, "Alice");
        assertFalse(r1.isSuccess());
        assertEquals("INVALID_PAYLOAD", r1.getErrorCode());

        JoinResult r2 = manager.joinClassroom("ROOM1", null);
        assertFalse(r2.isSuccess());
        assertEquals("INVALID_PAYLOAD", r2.getErrorCode());

        JoinResult r3 = manager.joinClassroom("ROOM1", "  ");
        assertFalse(r3.isSuccess());
        assertEquals("INVALID_PAYLOAD", r3.getErrorCode());
    }

    @Test
    @DisplayName("9. Classroom student membership is updated correctly")
    void testStudentMembershipUpdates() {
        manager.createClassroom("Lecturer A", "ROOM-X");
        assertEquals(0, manager.getStudents("ROOM-X").size());

        manager.joinClassroom("ROOM-X", "Student 1");
        assertEquals(1, manager.getStudents("ROOM-X").size());

        manager.joinClassroom("ROOM-X", "Student 2");
        List<User> students = manager.getStudents("ROOM-X");
        assertEquals(2, students.size());

        // getStudents returns an uncoupled copy
        students.clear();
        assertEquals(2, manager.getStudents("ROOM-X").size());
    }

    @Test
    @DisplayName("10. ClassroomMessageHandler processes CREATE_CLASSROOM requests and returns CLASSROOM_CREATED")
    void testMessageHandlerCreateClassroom() {
        Message request = new Message(MessageType.CREATE_CLASSROOM, "req-create-1");
        request.putPayload("lecturerName", "Prof. Hopper");

        Message response = handler.handleRequest(request);

        assertNotNull(response);
        assertEquals(MessageType.CLASSROOM_CREATED, response.getType());
        assertEquals("req-create-1", response.getRequestId());
        assertNotNull(response.getPayloadString("classroomCode"));
        assertNotNull(response.getPayloadString("lecturerId"));
        assertTrue(manager.hasClassroom(response.getPayloadString("classroomCode")));
    }

    @Test
    @DisplayName("11. ClassroomMessageHandler processes JOIN_CLASSROOM requests and returns CLASSROOM_JOINED")
    void testMessageHandlerJoinClassroom() {
        Classroom c = manager.createClassroom("Prof. Hopper", "HOP101");

        Message request = new Message(MessageType.JOIN_CLASSROOM, "req-join-1");
        request.putPayload("classroomCode", "HOP101");
        request.putPayload("studentName", "Grace");

        Message response = handler.handleRequest(request);

        assertNotNull(response);
        assertEquals(MessageType.CLASSROOM_JOINED, response.getType());
        assertEquals("req-join-1", response.getRequestId());
        assertEquals("HOP101", response.getPayloadString("classroomCode"));
        assertEquals("Grace", response.getPayloadString("studentName"));
        assertNotNull(response.getPayloadString("studentId"));
    }

    @Test
    @DisplayName("12. ClassroomMessageHandler returns ERROR on missing fields or invalid classroom")
    void testMessageHandlerErrors() {
        // Missing lecturerName
        Message reqMissingLecturer = new Message(MessageType.CREATE_CLASSROOM, "req-err-1");
        Message resp1 = handler.handleRequest(reqMissingLecturer);
        assertEquals(MessageType.ERROR, resp1.getType());
        assertEquals("INVALID_PAYLOAD", resp1.getPayloadString("code"));

        // Join non-existent room
        Message reqBadRoom = new Message(MessageType.JOIN_CLASSROOM, "req-err-2");
        reqBadRoom.putPayload("classroomCode", "DOESNOTEXIST");
        reqBadRoom.putPayload("studentName", "Grace");
        Message resp2 = handler.handleRequest(reqBadRoom);
        assertEquals(MessageType.ERROR, resp2.getType());
        assertEquals("INVALID_CLASSROOM", resp2.getPayloadString("code"));

        // Duplicate join error
        manager.createClassroom("Prof. X", "ROOM-DUP");
        manager.joinClassroom("ROOM-DUP", "Bob");

        Message reqDup = new Message(MessageType.JOIN_CLASSROOM, "req-err-3");
        reqDup.putPayload("classroomCode", "ROOM-DUP");
        reqDup.putPayload("studentName", "Bob");
        Message resp3 = handler.handleRequest(reqDup);
        assertEquals(MessageType.ERROR, resp3.getType());
        assertEquals("DUPLICATE_STUDENT", resp3.getPayloadString("code"));
    }
}
