package io.gigasource.p2p_client;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.TargetDeviceUnavailableException;
import io.socket.client.Ack;
import io.socket.client.Manager;
import io.socket.client.Socket;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class P2pClientPlugin extends Socket {
    private String targetClientId;
    private String options; // not yet used

    private P2pClientPlugin(Manager io, String nsp, Manager.Options opts) {
        super(io, nsp, opts);

        on(SocketEvent.P2P_REGISTER, (sourceClientId) -> {
            if (targetClientId == null) {
                this.targetClientId = sourceClientId.toString();
                emit(SocketEvent.P2P_REGISTER_SUCCESS);
            } else {
                emit(SocketEvent.P2P_REGISTER_FAILED);
            }
        });

        on(SocketEvent.P2P_DISCONNECT, (args) -> {
            targetClientId = null;
        });
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

    public void registerP2pTarget(String targetDeviceId, String options) {
        CountDownLatch connectionLatch = new CountDownLatch(1);


        CompletableFuture<Void> connectionResult = CompletableFuture.runAsync(() ->
                emit(SocketEvent.P2P_REGISTER, new Object[]{targetDeviceId}, args -> {
                    if ((boolean) args[0]) { // if target is available
                        this.options = options;
                        this.targetClientId = targetDeviceId;
                        connectionLatch.countDown();
                    } else {
                        throw new TargetDeviceUnavailableException("Target device is not available for connection");
                    }
                }));

        try {
            connectionLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
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
}
