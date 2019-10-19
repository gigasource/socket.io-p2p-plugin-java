package io.gigasource.p2p_client.api.one_to_one_connection;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Duplex {
    private Socket socket;
    private P2pMessage p2pMessageApi;
    private List<OutputStream> outputStreams;
    private InputStream inputStream;
    private int emitChunkSize = 1024;
    private Thread inputScanThread;
    private final Object inputReadThreadLock = new Object();
    private boolean destroyed;

    public Duplex(Socket socket, P2pMessage p2pMessageApi) {
        outputStreams = new ArrayList<>();

        this.socket = socket;
        this.p2pMessageApi = p2pMessageApi;

        addSocketListeners();
    }

    // Socket.IO listeners
    private void addSocketListeners() {
        socket.on(SocketEvent.P2P_EMIT_STREAM, args -> {
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
        });

        socket.once("disconnect", destroyListener);
        socket.once(SocketEvent.P2P_DISCONNECT, destroyListener);
        socket.once(SocketEvent.P2P_UNREGISTER, destroyListener);
    }

    public void destroy() {
        socket.off("disconnect", destroyListener);
        socket.off(SocketEvent.P2P_DISCONNECT, destroyListener);
        socket.off(SocketEvent.P2P_UNREGISTER, destroyListener);
        socket.off(SocketEvent.P2P_EMIT_STREAM);

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

        destroyed = true;
    }

    private Emitter.Listener destroyListener = args -> destroy();

    // ------------------------------------------------------------------------

    // Methods for Streams

    public void addOutputStream(OutputStream outputStream) {
        outputStreams.add(outputStream);
    }

    public void removeOutputStream(OutputStream outputStream) {
        outputStreams.remove(outputStream);
    }

    public void setInputStream(InputStream inputStream) {
        if (inputScanThread != null) inputScanThread.interrupt();

        this.inputStream = inputStream;
        startScanningInputStream();
    }

    public void removeInputStream() {
        if (inputScanThread != null) inputScanThread.interrupt();
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
                            p2pMessageApi.emit2(SocketEvent.P2P_EMIT_STREAM, chunkToEmit, (Ack) args -> {
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
}
