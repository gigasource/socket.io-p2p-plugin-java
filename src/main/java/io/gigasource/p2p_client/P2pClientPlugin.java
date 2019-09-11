package io.gigasource.p2p_client;

import io.gigasource.p2p_client.constants.SocketEvent;
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

public class P2pClientPlugin extends Socket {
    private String targetClientId;
    private String options; // not yet used

    private P2pClientPlugin(Manager io, String nsp, Manager.Options opts) {
        super(io, nsp, opts);

        on(SocketEvent.P2P_REGISTER, (args) -> {
            if (targetClientId == null) {
                this.targetClientId = (String) args[0];
                emit(SocketEvent.P2P_REGISTER_SUCCESS);
            } else {
                emit(SocketEvent.P2P_REGISTER_FAILED);
            }
        });

        on(SocketEvent.P2P_DISCONNECT, args -> targetClientId = null);
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

    public void unregisterP2pTarget() {
        if (this.targetClientId != null) {
            emit("disconnect");
            this.targetClientId = null;
        }
    }

    public boolean registerP2pTarget(String targetClientId, String options) {
        CompletableFuture<Boolean> connectSuccess = new CompletableFuture<>();

        emit(SocketEvent.P2P_REGISTER, new Object[]{targetClientId}, args -> {
            if ((boolean) args[0]) { // if target is available
                this.options = options;
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

                emitPayload.put("args", newArgs);
            } else {
                emitPayload.put("args", args);
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
}
