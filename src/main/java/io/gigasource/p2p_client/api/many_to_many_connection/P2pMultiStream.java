package io.gigasource.p2p_client.api.many_to_many_connection;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.socket.client.Ack;
import io.socket.client.Socket;
import java9.util.concurrent.CompletableFuture;
import java9.util.function.Consumer;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class P2pMultiStream {
    private Socket socket;
    private P2pMultiMessage p2pMultiMessageApi;
    private String clientId;

    public P2pMultiStream(Socket socket, P2pMultiMessage p2pMultiMessageApi) {
        this.socket = socket;
        this.p2pMultiMessageApi = p2pMultiMessageApi;
        clientId = p2pMultiMessageApi.getClientId();

        socket.on(SocketEvent.MULTI_API_CREATE_STREAM, args -> ((Ack) args[0]).call(false));
    }

    public Duplex addP2pStream(String targetClientId) {
        JSONObject payload = new JSONObject();
        String sourceStreamId = UUID.randomUUID().toString();
        String targetStreamId = UUID.randomUUID().toString();

        try {
            payload.put("sourceStreamId", sourceStreamId);
            payload.put("targetStreamId", targetStreamId);
            payload.put("sourceClientId", clientId);
            payload.put("targetClientId", targetClientId);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        CompletableFuture<Duplex> lock = new CompletableFuture<>();

        socket.emit(SocketEvent.MULTI_API_CREATE_STREAM, new Object[]{payload}, args -> {
            if ((boolean) args[0]) {
                Duplex duplexStream = new Duplex(socket, p2pMultiMessageApi, targetClientId, sourceStreamId, targetStreamId);
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

    public void onAddP2pStream(Consumer<Duplex> callback) {
        offAddP2pStream();
        socket.on(SocketEvent.MULTI_API_CREATE_STREAM, args -> {
            JSONObject connectionInfo = (JSONObject) args[0];
            String targetClientId;
            String sourceStreamId;
            String targetStreamId;

            try {
                targetClientId = (String) connectionInfo.get("sourceClientId");
                sourceStreamId = (String) connectionInfo.get("targetStreamId");
                targetStreamId = (String) connectionInfo.get("sourceStreamId");
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            Duplex duplexStream = new Duplex(socket, p2pMultiMessageApi, targetClientId, sourceStreamId, targetStreamId);
            if (callback != null) callback.accept(duplexStream); // return a Duplex to the listening client
            ((Ack) args[1]).call(true); // return result to peer to create stream on the other end of the connection
        });
    }

    public void offAddP2pStream() {
        socket.off(SocketEvent.MULTI_API_CREATE_STREAM);
    }
}
