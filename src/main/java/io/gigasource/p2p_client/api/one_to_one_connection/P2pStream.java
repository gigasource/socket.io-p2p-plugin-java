package io.gigasource.p2p_client.api.one_to_one_connection;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.socket.client.Ack;
import io.socket.client.Socket;
import java9.util.concurrent.CompletableFuture;
import java9.util.function.Consumer;

import java.util.concurrent.ExecutionException;

public class P2pStream {
    private Socket socket;
    private P2pMessage p2pMessageApi;
    private String targetClientId;

    public P2pStream(Socket socket, P2pMessage p2pMessageApi) {
        this.socket = socket;
        this.p2pMessageApi = p2pMessageApi;
        this.targetClientId = p2pMessageApi.getTargetClientId();

        socket.on(SocketEvent.P2P_REGISTER_STREAM, args -> ((Ack) args[0]).call(false));
    }

    public Duplex registerP2pStream() {
        CompletableFuture<Duplex> lock = new CompletableFuture<>();

        socket.emit(SocketEvent.P2P_REGISTER_STREAM, new Object[]{this.targetClientId}, args -> {
            if ((boolean) args[0]) {
                Duplex duplexStream = new Duplex(socket, p2pMessageApi);
                lock.complete(duplexStream);
            } else {
                lock.complete(null);
            }
        });

        try {
            return lock.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void onRegisterP2pStream(Consumer<Duplex> callback) {
        socket.off(SocketEvent.P2P_REGISTER_STREAM);
        socket.on(SocketEvent.P2P_REGISTER_STREAM, (args) -> {
            Duplex duplexStream = new Duplex(socket, p2pMessageApi);
            if (callback != null) callback.accept(duplexStream); // return a Duplex to the listening client
            ((Ack) args[0]).call(true); // return result to peer to create stream on the other end of the connection
        });
    }

    public void offRegisterP2pStream() {
        socket.off(SocketEvent.P2P_REGISTER_STREAM);
    }
}
