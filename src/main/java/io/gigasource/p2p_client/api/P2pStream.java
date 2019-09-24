package io.gigasource.p2p_client.api;

import io.gigasource.p2p_client.P2pClientPlugin;
import io.gigasource.p2p_client.constants.SocketEvent;
import io.socket.client.Ack;
import io.socket.emitter.Emitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class P2pStream {
    private P2pClientPlugin p2pClientPlugin;
    private List<OutputStream> outputStreams;
    private InputStream inputStream;
    private int emitChunkSize = 1024;
    private Thread inputScanThread;
    private final Object inputReadThreadLock = new Object();
    private boolean destroyed;

    public P2pStream(P2pClientPlugin p2pClientPlugin) {
        outputStreams = new ArrayList<>();

        this.p2pClientPlugin = p2pClientPlugin;

        addSocketListeners();
    }

    // Socket.IO listeners
    private void addSocketListeners() {
        p2pClientPlugin.on(SocketEvent.P2P_EMIT_STREAM, args -> {
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

        p2pClientPlugin.once("disconnect", destroyListener);
        p2pClientPlugin.once(SocketEvent.P2P_DISCONNECT, destroyListener);
        p2pClientPlugin.once(SocketEvent.P2P_UNREGISTER, destroyListener);
    }

    public void destroy() {
        p2pClientPlugin.off("disconnect", destroyListener);
        p2pClientPlugin.off(SocketEvent.P2P_DISCONNECT, destroyListener);
        p2pClientPlugin.off(SocketEvent.P2P_UNREGISTER, destroyListener);
        p2pClientPlugin.off(SocketEvent.P2P_EMIT_STREAM);

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
                            p2pClientPlugin.emit2(SocketEvent.P2P_EMIT_STREAM, chunkToEmit, (Ack) args -> {
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

    public P2pClientPlugin getP2pClientPlugin() {
        return p2pClientPlugin;
    }

    public boolean isDestroyed() {
        return destroyed;
    }
}
