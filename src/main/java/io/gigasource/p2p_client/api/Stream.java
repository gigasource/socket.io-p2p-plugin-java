package io.gigasource.p2p_client.api;

import io.gigasource.p2p_client.api.object.stream.Duplex;
import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.P2pStreamException;
import io.socket.client.Ack;
import io.socket.client.Socket;
import java9.util.concurrent.CompletableFuture;
import java9.util.function.Consumer;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class Stream {
    private Socket socket;
    private Message messageApi;
    private String clientId;

    public Stream(Socket socket, Message messageApi) {
        this.socket = socket;
        this.messageApi = messageApi;
        clientId = messageApi.getClientId();

        socket.on(SocketEvent.MULTI_API_CREATE_STREAM, args ->
                ((Ack) args[1]).call("Client is not listening to create stream event"));
    }

    public Duplex addP2pStream(String targetClientId) throws P2pStreamException {
        JSONObject payload = new JSONObject();
        String sourceStreamId = UUID.randomUUID().toString();
        String targetStreamId = UUID.randomUUID().toString();

        try {
            payload.put("sourceStreamId", sourceStreamId);
            payload.put("targetStreamId", targetStreamId);
            // sourceClientId will be set on server
            payload.put("targetClientId", targetClientId);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        CompletableFuture<String> lock = new CompletableFuture<>();

        socket.emit(SocketEvent.MULTI_API_CREATE_STREAM, payload, (Ack) args -> {
            if (args.length == 0) lock.complete(null);
            else lock.complete(args[0].toString());
        });

        String err =  lock.join();
        if (err != null) throw new P2pStreamException(err);
        else return new Duplex(socket, messageApi, targetClientId, sourceStreamId, targetStreamId);
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

            Duplex duplex = new Duplex(socket, messageApi, targetClientId, sourceStreamId, targetStreamId);
            if (callback != null) callback.accept(duplex); // return a Duplex to the listening client
            ((Ack) args[1]).call(); // notify peer that the duplex has been created
        });
    }

    public void offAddP2pStream() {
        socket.off(SocketEvent.MULTI_API_CREATE_STREAM);
    }
}
