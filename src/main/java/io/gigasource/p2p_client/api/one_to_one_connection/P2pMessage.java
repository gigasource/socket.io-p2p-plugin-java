package io.gigasource.p2p_client.api.one_to_one_connection;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.InvalidConnectionStateException;
import io.gigasource.p2p_client.exception.InvalidSocketEventException;
import io.gigasource.p2p_client.exception.InvalidTargetClientException;
import io.socket.client.Ack;
import io.socket.client.Socket;
import java9.util.concurrent.CompletableFuture;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class P2pMessage {
    private String targetClientId;
    private Socket socket;
    private String clientId;

    public P2pMessage(Socket socket, String clientId) {
        this.socket = socket;
        this.clientId = clientId;

        socket.on(SocketEvent.P2P_REGISTER, args -> {
            if (targetClientId == null || targetClientId.equals(args[0])) {
                this.targetClientId = (String) args[0];
                ((Ack) args[1]).call(true);
            } else {
                ((Ack) args[1]).call(false);
            }
        });

        socket.on(SocketEvent.P2P_DISCONNECT, args -> {
            if (this.targetClientId != null) targetClientId = null;
        });

        socket.on(SocketEvent.P2P_UNREGISTER, args -> {
            if (this.targetClientId != null) {
                targetClientId = null;
                ((Ack) args[0]).call();
            }
        });

        socket.on(SocketEvent.SERVER_ERROR, args ->
                System.out.println((String) args[0]));
    }

    public void unregisterP2pTarget() {
        if (this.targetClientId == null) return;

        CompletableFuture<Void> lock = new CompletableFuture<>();

        socket.emit(SocketEvent.P2P_UNREGISTER, new Object[]{}, args -> lock.complete(null));
        this.targetClientId = null;

        try {
            lock.get(); // pause code execution until the lock completes
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public boolean registerP2pTarget(String targetClientId, String connectionOpts) {
        if (targetClientId.equals(this.clientId))
            throw new InvalidTargetClientException("Target client ID can not be the same as source client ID");

        if (this.targetClientId != null)
            throw new InvalidConnectionStateException("Client is in connection, please unregister before registering again");

        CompletableFuture<Boolean> connectSuccess = new CompletableFuture<>();

        socket.emit(SocketEvent.P2P_REGISTER, new Object[]{targetClientId}, args -> {
            if ((boolean) args[0]) { // if target is available
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
            throw new InvalidTargetClientException("socket.emit2 must be called after targetClientId is set");

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
            socket.emit(SocketEvent.P2P_EMIT_ACKNOWLEDGE, emitPayload, ack);
        } else { // No Ack case
            socket.emit(SocketEvent.P2P_EMIT, emitPayload);
        }
    }

    public List<String> getClientList() {
        CompletableFuture<List<String>> clientListRequest = new CompletableFuture<>();

        socket.emit(SocketEvent.LIST_CLIENTS, new Object[]{}, args -> {
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

    public String getClientId() {
        return clientId;
    }
}
