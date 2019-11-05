package io.gigasource.p2p_client.api.object.stream;

import io.gigasource.p2p_client.api.Message;
import io.gigasource.p2p_client.constants.SocketEvent;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java9.util.function.Consumer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Duplex {
    private Socket socket;
    private Message messageApi;
    private List<OutputStream> outputStreams;
    private InputStream inputStream;
    private int emitChunkSize = 1024;
    private Thread inputScanThread;
    private final Object inputReadThreadLock = new Object();
    private boolean destroyed;
    private List<Consumer<Void>> destroyCallbacks;

    private String targetClientId;
    private String sourceStreamId;
    private String targetStreamId;

    public Duplex(Socket socket, Message messageApi, String targetClientId, String sourceStreamId, String targetStreamId) {
        outputStreams = new ArrayList<>();

        this.socket = socket;
        this.messageApi = messageApi;
        this.targetClientId = targetClientId;
        this.sourceStreamId = sourceStreamId;
        this.targetStreamId = targetStreamId;
        this.destroyCallbacks = new ArrayList<>();

        addSocketListeners();
    }

    private Emitter.Listener onReceiveStreamData = args -> {
        byte[] chunk = (byte[]) args[0];
        Ack ackFn = (Ack) args[1];

        try {
            for (OutputStream outputStream : outputStreams) {
                outputStream.write(chunk);
                outputStream.flush();
            }
        } catch (IOException e) {
            System.err.println("Encounter error while writing to one of outputStreams");
            e.printStackTrace();
        }

        ackFn.call();
    };

    private Emitter.Listener onDisconnect = args -> destroy();

    private Emitter.Listener onTargetDisconnect = args -> {
        String targetClientId = (String) args[0];

        if (this.targetClientId.equals(targetClientId)) destroy();
    };

    private Emitter.Listener onTargetStreamDestroyed = args -> {
        String targetStreamId = (String) args[0];

        if (this.targetStreamId.equals(targetStreamId)) destroy();
    };

    // Socket.IO listeners
    private void addSocketListeners() {
        String sendDataEvent = SocketEvent.P2P_EMIT_STREAM + SocketEvent.STREAM_IDENTIFIER_PREFIX + targetStreamId;

        messageApi.from(targetClientId).on(sendDataEvent, onReceiveStreamData);
        messageApi.from(targetClientId).on(SocketEvent.PEER_STREAM_DESTROYED, onTargetStreamDestroyed);

        socket.once("disconnect", onDisconnect);
        socket.on(SocketEvent.MULTI_API_TARGET_DISCONNECT, onTargetDisconnect);
    }

    private void removeSocketListeners() {
        String sendDataEvent = SocketEvent.P2P_EMIT_STREAM + SocketEvent.STREAM_IDENTIFIER_PREFIX + targetStreamId;

        messageApi.from(targetClientId).off(sendDataEvent);
        messageApi.from(targetClientId).off(SocketEvent.PEER_STREAM_DESTROYED, onTargetStreamDestroyed);

        socket.off("disconnect", onDisconnect);
        socket.off(SocketEvent.MULTI_API_TARGET_DISCONNECT, onTargetDisconnect);
    }

    public void destroy() {
        if (destroyed) return;

        messageApi.emitTo(targetClientId, SocketEvent.PEER_STREAM_DESTROYED, sourceStreamId);
        removeSocketListeners();
        try {
            if (inputScanThread != null) inputScanThread.interrupt();
            if (inputStream != null) inputStream.close();
            for (OutputStream os : outputStreams) {
                os.flush();
            }
            outputStreams.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!destroyCallbacks.isEmpty()) {
            for (Consumer<Void> destroyCallback : destroyCallbacks) {
                destroyCallback.accept(null);
            }
            destroyCallbacks.clear();
        }

        destroyed = true;
    }

    // ------------------------------------------------------------------------

    // Methods for Streams

    public void addOutputStream(OutputStream outputStream) {
        outputStreams.add(outputStream);
    }

    public void removeOutputStream(OutputStream outputStream) {
        outputStreams.remove(outputStream);
    }

    public void clearOutputStreams() { outputStreams.clear(); }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
        if (inputScanThread == null) startScanningInputStream();
    }

    public void removeInputStream() {
        inputStream = null;
    }

    private void startScanningInputStream() {
        inputScanThread = new Thread(() -> {
            synchronized (inputReadThreadLock) {
                try {
                    byte[] chunk = new byte[emitChunkSize];
                    int readLength;

                    while ((readLength = inputStream.read(chunk)) != -1) {
                        if (readLength > 0) {
                            byte[] chunkToEmit = Arrays.copyOfRange(chunk, 0, readLength);

                            String event = SocketEvent.P2P_EMIT_STREAM + "-from-stream-" + sourceStreamId;

                            messageApi.emitTo(targetClientId, event, chunkToEmit, (Ack) args -> {
                                synchronized (inputReadThreadLock) {
                                    inputReadThreadLock.notify();
                                }
                            });
                            inputReadThreadLock.wait();
                        }
                    }
                } catch (IOException e) {
                    System.err.println("inputScanThread encounters error while reading inputStream");
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    System.out.println("inputScanThread is requested for termination, will shut down gracefully");
                }
            }
        });

        inputScanThread.start();
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void onDestroy(Consumer<Void> callback) {
        destroyCallbacks.add(callback);
    }

    public String getTargetClientId() {
        return targetClientId;
    }

    public String getTargetStreamId() {
        return targetStreamId;
    }
}
