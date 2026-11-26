package com.classpulse.server.nio;

import com.classpulse.common.protocol.FrameEncoder;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NioServerTest {

    private NioServer server;
    private Thread serverThread;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.join(2000);
        }
    }

    private int startServer(MessageHandler handler) throws InterruptedException {
        server = new NioServer(0, handler);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException ignored) {
            }
        }, "test-nio-server");
        serverThread.start();

        long deadline = System.currentTimeMillis() + 3000;
        while (!server.isRunning() || server.getPort() <= 0) {
            if (System.currentTimeMillis() > deadline) {
                fail("Server did not start in time");
            }
            Thread.sleep(10);
        }
        return server.getPort();
    }

    @Test
    @DisplayName("Server starts, binds to port, and accepts single and multiple clients")
    void testServerStartsAndAcceptsMultipleClients() throws Exception {
        int port = startServer((client, msg) -> {});
        assertTrue(server.isRunning());
        assertTrue(port > 0);

        try (Socket c1 = new Socket(); Socket c2 = new Socket()) {
            c1.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            c2.connect(new InetSocketAddress("127.0.0.1", port), 2000);

            long deadline = System.currentTimeMillis() + 2000;
            while (server.getActiveConnectionCount() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(2, server.getActiveConnectionCount());
        }
    }

    @Test
    @DisplayName("Server receives and decodes a complete message")
    void testReceiveCompleteMessage() throws Exception {
        List<Message> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        int port = startServer((client, msg) -> {
            received.add(msg);
            latch.countDown();
        });

        Message expected = new Message(MessageType.HEARTBEAT, "hb-1");
        ByteBuffer frame = FrameEncoder.encode(expected);
        byte[] bytes = new byte[frame.remaining()];
        frame.get(bytes);

        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(bytes);
            out.flush();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, received.size());
            assertEquals(expected, received.get(0));
        }
    }

    @Test
    @DisplayName("Server handles a message arriving in multiple TCP reads")
    void testPartialTcpReads() throws Exception {
        List<Message> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        int port = startServer((client, msg) -> {
            received.add(msg);
            latch.countDown();
        });

        Message expected = new Message(MessageType.CREATE_CLASSROOM, "req-split");
        expected.putPayload("lecturerName", "Prof. Ada");
        ByteBuffer frame = FrameEncoder.encode(expected);
        byte[] bytes = new byte[frame.remaining()];
        frame.get(bytes);

        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            // Send 2 bytes of header, then 6 bytes, then the rest
            out.write(bytes, 0, 2);
            out.flush();
            Thread.sleep(20);

            out.write(bytes, 2, 6);
            out.flush();
            Thread.sleep(20);

            out.write(bytes, 8, bytes.length - 8);
            out.flush();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, received.size());
            assertEquals(expected, received.get(0));
        }
    }

    @Test
    @DisplayName("Server handles client disconnect and continues serving remaining clients")
    void testClientDisconnectAndServerContinues() throws Exception {
        List<Message> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        int port = startServer((client, msg) -> {
            received.add(msg);
            latch.countDown();
        });

        Socket c1 = new Socket("127.0.0.1", port);
        Socket c2 = new Socket("127.0.0.1", port);

        long deadline = System.currentTimeMillis() + 2000;
        while (server.getActiveConnectionCount() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(2, server.getActiveConnectionCount());

        // Disconnect first client
        c1.close();
        deadline = System.currentTimeMillis() + 2000;
        while (server.getActiveConnectionCount() > 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, server.getActiveConnectionCount());
        assertTrue(server.isRunning());

        // Second client can still send messages
        Message msg = new Message(MessageType.SUCCESS, "req-after-disconnect").putPayload("message", "still alive");
        ByteBuffer frame = FrameEncoder.encode(msg);
        byte[] bytes = new byte[frame.remaining()];
        frame.get(bytes);

        c2.getOutputStream().write(bytes);
        c2.getOutputStream().flush();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(msg, received.get(0));
        c2.close();
    }
}
