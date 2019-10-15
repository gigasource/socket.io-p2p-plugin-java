package io.gigasource.p2p_client.api.many_to_many_connection;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.InvalidSocketEventException;
import io.gigasource.p2p_client.exception.InvalidTargetClientException;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java9.util.function.Function;

public class P2pMultiMessage {
    private Socket socket;
    private String clientId;
    private Map<String, List<String>> listenerMap;
    private String currentTargetId;

    public P2pMultiMessage(Socket socket, String clientId) {
        this.socket = socket;
        this.clientId = clientId;

        listenerMap = new HashMap<>();

        socket.on(SocketEvent.MULTI_API_TARGET_DISCONNECT, args -> {
            String sourceClientId = (String) args[0];
            List<String> listenerNames = listenerMap.get(sourceClientId);
            if (listenerNames != null && !listenerNames.isEmpty()) {
                for (String listenerName : listenerNames) {
                    socket.off(listenerName);
                }
                listenerMap.remove(sourceClientId);
            }
        });
    }

    public void addP2pTarget(String targetClientId) {
        JSONObject payload = new JSONObject();

        try {
            payload.put("sourceClientId", clientId);
            payload.put("targetClientId", targetClientId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        socket.emit(SocketEvent.MULTI_API_ADD_TARGET, payload);
    }

    public void onAddP2pTarget(Function<String, ?> callback) {
        socket.on(SocketEvent.MULTI_API_ADD_TARGET, args -> callback.apply((String) args[0]));
    }

    public P2pMultiMessage from(String targetClientId) {
        currentTargetId = targetClientId;
        return this;
    }

    public void on(String event, Emitter.Listener callback) {
        event = event + "-from-" + currentTargetId;
        socket.on(event, callback);

        listenerMap.computeIfAbsent(currentTargetId, k -> new ArrayList<>());
        listenerMap.get(currentTargetId).add(event);
    }

    public void emitTo(String targetClientId, String event, Object... args) {
        if (targetClientId == null)
            throw new InvalidTargetClientException("socket.emit2 must be called after targetClientId is set");

        if (event == null)
            throw new InvalidSocketEventException("event must be specified");

        int lastIndex = args.length - 1;
        boolean isAckCase = args.length > 0 && args[lastIndex] instanceof Ack;

        JSONObject emitPayload = new JSONObject();
        try {
            emitPayload.put("targetClientId", targetClientId);
            emitPayload.put("event", event + "-from-" + clientId);

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

    public String getClientId() {
        return clientId;
    }
}
