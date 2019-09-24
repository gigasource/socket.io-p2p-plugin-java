package io.gigasource.p2p_client;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.InvalidConnectionStateException;
import io.gigasource.p2p_client.exception.InvalidSocketEventException;
import io.gigasource.p2p_client.exception.InvalidTargetClientException;
import io.gigasource.p2p_client.api.P2pStream;
import io.socket.client.Ack;
import io.socket.client.Manager;
import io.socket.client.Socket;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class P2pClientPlugin extends Socket {
    private String targetClientId;
    private String connectionOpts; // not yet used
    private String id;

    private P2pClientPlugin(Manager io, String nsp, Manager.Options opts) {
        super(io, nsp, opts);

        on(SocketEvent.P2P_REGISTER, args -> {
            if (targetClientId == null || targetClientId.equals(args[0])) {
                this.targetClientId = (String) args[0];
                ((Ack) args[1]).call(true);
            } else {
                ((Ack) args[1]).call(false);
            }
        });

        on(SocketEvent.P2P_DISCONNECT, args -> {
            if (this.targetClientId != null) targetClientId = null;
        });

        on(SocketEvent.P2P_UNREGISTER, args -> {
            if (this.targetClientId != null) {
                targetClientId = null;
                ((Ack) args[0]).call();
            }
        });

        on(SocketEvent.SERVER_ERROR, args ->
                System.out.println((String) args[0]));

        on(SocketEvent.P2P_REGISTER_STREAM, args -> ((Ack) args[0]).call(false));
    }

    public static P2pClientPlugin createInstance(Socket socket) {
        try {
            Manager io = socket.io();
            String nsp = (String) FieldUtils.readField(socket, "nsp", true);
            Manager.Options opts = (Manager.Options) FieldUtils.readField(io, "opts", true);

            return new P2pClientPlugin(io, nsp, opts);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static P2pClientPlugin createInstance(Socket socket, String clientId) {
        P2pClientPlugin instance = createInstance(socket);

        if (instance == null) return null;

        instance.id = clientId;
        return instance;
    }

    public void unregisterP2pTarget() {
        if (this.targetClientId == null) return;

        CompletableFuture<Void> lock = new CompletableFuture<>();

        emit(SocketEvent.P2P_UNREGISTER, new Object[]{}, args -> lock.complete(null));
        this.targetClientId = null;

        try {
            lock.get(); // pause code execution until the lock completes
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public boolean registerP2pTarget(String targetClientId, String connectionOpts) {
        if (targetClientId.equals(this.id))
            throw new InvalidTargetClientException("Target client ID can not be the same as source client ID");

        if (this.targetClientId != null)
            throw new InvalidConnectionStateException("Client is in connection, please unregister before registering again");

        CompletableFuture<Boolean> connectSuccess = new CompletableFuture<>();

        emit(SocketEvent.P2P_REGISTER, new Object[]{targetClientId}, args -> {
            if ((boolean) args[0]) { // if target is available
                this.connectionOpts = connectionOpts;
                this.targetClientId = targetClientId;
                connectSuccess.complete(true);
            } else {
                connectSuccess.complete(false);
            }

        });

        try {
            return connectSuccess.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void emit2(String event, Object... args) {
        if (this.targetClientId == null)
            throw new InvalidTargetClientException("emit2 must be called after targetClientId is set");

        if (event == null)
            throw new InvalidSocketEventException("event must be specified");

        int lastIndex = args.length - 1;
        boolean isAckCase = args.length > 0 && args[lastIndex] instanceof Ack;

        JSONObject emitPayload = new JSONObject();
        try {
            emitPayload.put("targetClientId", targetClientId);
            emitPayload.put("event", event);

            if (isAckCase) {
                Object[] newArgs = new Object[lastIndex];
                for (int i = 0; i < lastIndex; i++) {
                    newArgs[i] = args[i];
                }

                emitPayload.put("args", new JSONArray(newArgs));
            } else {
                emitPayload.put("args", new JSONArray(args));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (isAckCase) { // Ack case
            Ack ack = (Ack) args[lastIndex];
            emit(SocketEvent.P2P_EMIT_ACKNOWLEDGE, emitPayload, ack);
        } else { // No Ack case
            emit(SocketEvent.P2P_EMIT, emitPayload);
        }
    }

    public P2pStream registerP2pStream() {
        CompletableFuture<P2pStream> lock = new CompletableFuture<>();

        emit(SocketEvent.P2P_REGISTER_STREAM, new Object[]{this.targetClientId}, args -> {
            if ((boolean) args[0]) {
                P2pStream duplexStream = new P2pStream(this);
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

    public void onRegisterP2pStream(Function<P2pStream, Void> callback) {
        off(SocketEvent.P2P_REGISTER_STREAM);
        on(SocketEvent.P2P_REGISTER_STREAM, (args) -> {
            P2pStream duplexStream = new P2pStream(this);
            if (callback != null) callback.apply(duplexStream); // return a Duplex to the listening client
            ((Ack) args[0]).call(true); // return result to peer to create stream on the other end of the connection
        });
    }

    public void offRegisterP2pStream() {
        off(SocketEvent.P2P_REGISTER_STREAM);
    }

    public List<String> getClientList() {
        CompletableFuture<List<String>> clientListRequest = new CompletableFuture<>();

        emit(SocketEvent.LIST_CLIENTS, new Object[]{}, args -> {
            JSONArray clients = (JSONArray) args[0];
            List<String> clientList = new ArrayList<>();

            for (int i = 0; i < clients.length(); i++) {
                try {
                    clientList.add((String) clients.get(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            clientListRequest.complete(clientList);
        });

        try {
            return clientListRequest.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getTargetClientId() {
        return targetClientId;
    }

    public String getId() {
        return id;
    }
}
