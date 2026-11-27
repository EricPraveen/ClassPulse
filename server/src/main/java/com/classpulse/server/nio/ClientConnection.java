package com.classpulse.server.nio;

import com.classpulse.common.protocol.FrameDecoder;
import com.classpulse.common.protocol.FrameEncoder;
import com.classpulse.common.protocol.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Maintains per-client state for a non-blocking TCP connection.
 * Each client has its own {@link FrameDecoder} and outgoing {@link ByteBuffer} queue.
 */
public class ClientConnection {

    private static final int READ_BUFFER_SIZE = 4096;

    private final SocketChannel channel;
    private final SelectionKey selectionKey;
    private final FrameDecoder frameDecoder;
    private final ByteBuffer readBuffer;
    private final Queue<ByteBuffer> writeQueue;
    private final String remoteAddress;
    private boolean closed;

    public ClientConnection(SocketChannel channel, SelectionKey selectionKey) {
        this.channel = channel;
        this.selectionKey = selectionKey;
        this.frameDecoder = new FrameDecoder();
        this.readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        this.writeQueue = new ArrayDeque<>();
        this.remoteAddress = resolveRemoteAddress(channel);
        this.closed = false;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public FrameDecoder getFrameDecoder() {
        return frameDecoder;
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Encodes a {@link Message} using {@link FrameEncoder} and queues it for non-blocking write.
     * Registers {@code OP_WRITE} on the selection key if not already registered.
     *
     * @param message the message to send
     */
    public void send(Message message) {
        if (closed || message == null) {
            return;
        }
        ByteBuffer frame = FrameEncoder.encode(message);
        synchronized (writeQueue) {
            writeQueue.add(frame);
        }
        if (selectionKey.isValid()) {
            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
            selectionKey.selector().wakeup();
        }
    }

    /**
     * Writes queued outgoing buffers to the channel without blocking.
     * Removes {@code OP_WRITE} when the queue becomes empty.
     *
     * @throws IOException if writing to the channel fails
     */
    public void flushWriteQueue() throws IOException {
        synchronized (writeQueue) {
            while (!writeQueue.isEmpty()) {
                ByteBuffer current = writeQueue.peek();
                channel.write(current);
                if (current.hasRemaining()) {
                    // Socket send buffer is full; wait for next OP_WRITE
                    return;
                }
                writeQueue.poll();
            }
            if (selectionKey.isValid()) {
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
            }
        }
    }

    /**
     * Closes the underlying channel and cancels the selection key.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        frameDecoder.reset();
        synchronized (writeQueue) {
            writeQueue.clear();
        }
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best-effort close
        }
    }

    private static String resolveRemoteAddress(SocketChannel channel) {
        try {
            return String.valueOf(channel.getRemoteAddress());
        } catch (IOException e) {
            return "unknown";
        }
    }
}
