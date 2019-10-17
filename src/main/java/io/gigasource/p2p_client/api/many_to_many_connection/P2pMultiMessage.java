package io.gigasource.p2p_client.api.many_to_many_connection;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.InvalidSocketEventException;
import io.gigasource.p2p_client.exception.InvalidTargetClientException;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java9.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public void onAddP2pTarget(Consumer<String> callback) {
        socket.on(SocketEvent.MULTI_API_ADD_TARGET, args -> callback.accept((String) args[0]));
    }

    public P2pMultiMessage from(String targetClientId) {
        currentTargetId = targetClientId;
        return this;
    }

    public void on(String event, Emitter.Listener callback) {
        String targetId = currentTargetId;
        event = event + "-from-" + targetId;
        socket.on(event, callback);

        if (!listenerMap.containsKey(targetId)) {
            listenerMap.put(targetId, new ArrayList<>());
        }
        listenerMap.get(targetId).add(event);
    }

    public void off(String event, Emitter.Listener callback) {
        String eventName = event + "-from-" + currentTargetId;

        if (callback != null) socket.off(eventName, callback);
        else socket.off(eventName);
    }

    public void emitTo(String targetClientId, String event, Object... args) {
        if (targetClientId == null)
            throw new InvalidTargetClientException("targetClientId can not be null");

        if (event == null)
            throw new InvalidSocketEventException("event can not be null");

        int lastIndex = args.length - 1;
        boolean isAckCase = args.length > 0 && args[lastIndex] instanceof Ack;

        JSONObject emitPayload = new JSONObject();
        try {
            emitPayload.put("targetClientId", targetClientId);
            emitPayload.put("event", event + "-from-" + clientId);

            if (isAckCase) {
                Object[] newArgs = new Object[lastIndex];
                System.arraycopy(args, 0, newArgs, 0, lastIndex);

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
