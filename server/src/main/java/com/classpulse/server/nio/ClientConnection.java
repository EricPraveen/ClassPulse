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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Maintains per-client state for a non-blocking TCP connection.
 * Each client has its own {@link FrameDecoder} and outgoing {@link ByteBuffer} queue.
 * <p>
 * Worker threads may safely enqueue responses via {@link #send(Message)}; actual channel
 * reads/writes and {@link SelectionKey} interest updates are performed on the selector thread.
 */
public class ClientConnection {

    private static final int READ_BUFFER_SIZE = 4096;

    private final SocketChannel channel;
    private final SelectionKey selectionKey;
    private final FrameDecoder frameDecoder;
    private final ByteBuffer readBuffer;
    private final Queue<ByteBuffer> writeQueue;
    private final String remoteAddress;
    private final Consumer<ClientConnection> writeInterestRequester;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ClientConnection(SocketChannel channel, SelectionKey selectionKey) {
        this(channel, selectionKey, null);
    }

    public ClientConnection(SocketChannel channel, SelectionKey selectionKey,
                            Consumer<ClientConnection> writeInterestRequester) {
        this.channel = channel;
        this.selectionKey = selectionKey;
        this.frameDecoder = new FrameDecoder();
        this.readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        this.writeQueue = new ArrayDeque<>();
        this.remoteAddress = resolveRemoteAddress(channel);
        this.writeInterestRequester = writeInterestRequester;
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
        return closed.get();
    }

    /**
     * Encodes a {@link Message} using {@link FrameEncoder} and queues it for non-blocking write.
     * Notifies the owning selector loop (when configured) so {@code OP_WRITE} is registered
     * safely on the selector thread.
     *
     * @param message the message to send
     */
    public void send(Message message) {
        if (closed.get() || message == null) {
            return;
        }
        ByteBuffer frame = FrameEncoder.encode(message);
        synchronized (writeQueue) {
            if (closed.get()) {
                return;
            }
            writeQueue.add(frame);
        }
        if (writeInterestRequester != null) {
            writeInterestRequester.accept(this);
        } else if (selectionKey != null && selectionKey.isValid()) {
            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
            selectionKey.selector().wakeup();
        }
    }

    /**
     * Enables {@code OP_WRITE} on this connection's selection key if frames are queued.
     * Must be invoked only on the NIO selector thread.
     */
    void enableWriteInterest() {
        if (closed.get() || selectionKey == null || !selectionKey.isValid()) {
            return;
        }
        synchronized (writeQueue) {
            if (!closed.get() && !writeQueue.isEmpty() && selectionKey.isValid()) {
                selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
            }
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
            if (selectionKey != null && selectionKey.isValid()) {
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
            }
        }
    }

    /**
     * Closes the underlying channel and cancels the selection key.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
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
