package io.gigasource.p2p_client.api;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.TargetClientException;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java9.util.function.Consumer;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java9.util.concurrent.CompletableFuture;

public class Message {
    private Socket socket;
    private String clientId;
    private String currentTargetId;
    private Map<String, List<Listener>> listenerMap;  // used for listeners created with from().on(), from().once();
    private Map<String, List<Listener>> listenerMapAny; // used for listeners created with onAny(), onceAny();

    // Example of listenerMap in JSON:
    // this.listenerMap = {
    //   clientA: [{event: 'getData', callback: () => {}, newCallback: () => {}}],
    //   clientB: [{event: 'doSomething', callback: () => {}, newCallback: () => {}}],
    // }

    // Example of listenerMapAny in JSON:
    // this.listenerMapAny = {
    //   getData: [{callback: () => {}, newCallback: () => {}}],
    //   doSomething: [{callback: () => {}, newCallback: () => {}}],
    // }

    public Message(Socket socket, String clientId) {
        this.socket = socket;
        this.clientId = clientId;
        listenerMap = new HashMap<>();
        listenerMapAny = new HashMap<>();

        socket.on(SocketEvent.MULTI_API_TARGET_DISCONNECT, (args) -> {
            String targetClientId = (String) args[0];
            List<Listener> listeners = listenerMap.get(targetClientId);

            if (listeners == null) return;

            for (Listener listener : listeners) socket.off(listener.getEvent(), listener.getNewCallback());

            listenerMap.remove(targetClientId);
        });

        socket.on(SocketEvent.SERVER_ERROR, (args) -> System.err.println(args[0]));
    }

    public void addP2pTarget(String targetClientId) throws TargetClientException {
        if (targetClientId == null) throw new IllegalArgumentException("targetClientId can not be null");

        CompletableFuture<String> lock = new CompletableFuture<>();

        socket.emit(SocketEvent.MULTI_API_ADD_TARGET, targetClientId, (Ack) (args) -> {
            if (args.length == 0) lock.complete(null);
            else lock.complete(args[0].toString());
        });

        String err = lock.join();
        if (err != null) throw new TargetClientException(err);
    }

    public void onAddP2pTarget(Consumer<String> callback) {
        if (callback == null) throw new IllegalArgumentException("callback can not be null");

        socket.on(SocketEvent.MULTI_API_ADD_TARGET, args -> {
            Ack peerCallback = (Ack) args[1];
            peerCallback.call();

            callback.accept((String) args[0]);
        });
    }

    public void onAny(String event, Emitter.Listener callback) {
        Emitter.Listener newCallback = args -> {
            Object[] newArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, newArgs, 0, args.length - 1);
            callback.call(newArgs);
        };

        Listener listener = new Listener(callback, newCallback);

        if (!this.listenerMapAny.containsKey(event)) {
            this.listenerMapAny.put(event, new ArrayList<>());
        }
        this.listenerMapAny.get(event).add(listener);
        socket.on(event, newCallback);
    }

    public void onceAny(String event, Emitter.Listener callback) {
        Emitter.Listener newCallback = args -> {
            Object[] newArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, newArgs, 0, args.length - 1);
            callback.call(newArgs);

            removeListeners(event, callback);
        };

        Listener listener = new Listener(callback, newCallback);

        if (!this.listenerMapAny.containsKey(event)) {
            this.listenerMapAny.put(event, new ArrayList<>());
        }
        this.listenerMapAny.get(event).add(listener);
        socket.once(event, newCallback);
    }

    public void offAny(String event, Emitter.Listener callback) {
        if (event == null && callback == null) {
            for (String ev : listenerMapAny.keySet()) socket.off(ev);
            listenerMapAny = new HashMap<>();
        } else {
            List<Listener> listeners = listenerMapAny.get(event);
            if (listeners == null) return;

            if (callback != null) {
                for (Listener listener : listeners) {
                    if (listener.getCallback() == callback) socket.off(event, listener.getNewCallback());
                }
                removeListeners(event, callback);
            } else {
                socket.off(event);
                listenerMapAny.remove(event);
            }
        }
    }

    public Message from(String targetClientId) {
        currentTargetId = targetClientId;
        return this;
    }

    public void on(String event, Emitter.Listener callback) {
        String targetId = currentTargetId;
        Emitter.Listener newCallback = args -> {
            if (!args[0].equals(targetId)) return;

            Object[] newArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, newArgs, 0, args.length - 1);
            callback.call(newArgs);
        };

        socket.on(event, newCallback);

        if (!listenerMap.containsKey(targetId)) {
            listenerMap.put(targetId, new ArrayList<>());
        }

        Listener listener = new Listener(event, callback, newCallback);
        listenerMap.get(targetId).add(listener);
    }

    public void once(String event, Emitter.Listener callback) {
        String targetId = currentTargetId;
        Emitter.Listener newCallback = args -> {
            if (!args[0].equals(targetId)) return;

            Object[] newArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, newArgs, 0, args.length - 1);
            callback.call(newArgs);
            removeListeners(targetId, event, callback);
        };

        socket.once(event, newCallback);

        if (!listenerMap.containsKey(targetId)) {
            listenerMap.put(targetId, new ArrayList<>());
        }

        Listener listener = new Listener(event, callback, newCallback);
        listenerMap.get(targetId).add(listener);
    }

    public void off(String event, Emitter.Listener callback) {
        String targetId = currentTargetId;
        List<Listener> listeners = listenerMap.get(targetId);
        if (listeners == null) return;

        if (callback != null) {
            for (Listener listener : listeners) {
                if (listener.getCallback() == callback && listener.event.equals(event))
                    socket.off(event, listener.newCallback);
            }
            removeListeners(targetId, event, callback);
        } else {
            for (Listener listener : listeners) {
                if (listener.event.equals(event)) socket.off(event, listener.newCallback);
            }
            removeListeners(targetId, event);
        }
    }

    public void off(String event) {
        off(event, null);
    }

    public void emitTo(String targetClientId, String event, Object... args) {
        int lastIndex = args.length - 1;
        boolean isAckCase = args.length > 0 && args[lastIndex] instanceof Ack;

        JSONObject emitPayload = new JSONObject();
        try {
            emitPayload.put("targetClientId", targetClientId);
            emitPayload.put("event", event);

            if (isAckCase) {
                Object[] newArgs = new Object[args.length];
                newArgs[0] = clientId;
                System.arraycopy(args, 0, newArgs, 1, lastIndex);
                emitPayload.put("args", new JSONArray(newArgs));
            } else {
                args = ArrayUtils.addAll(new Object[]{clientId}, args);
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

    private void removeListeners(String event, Emitter.Listener callback) {
        listenerMapAny.get(event).removeIf(listener -> listener.getCallback() == callback);
    }

    private void removeListeners(String targetClientId, String event) {
        listenerMap.get(targetClientId).removeIf(listener -> listener.getEvent().equals(event));
    }

    private void removeListeners(String targetClientId, String event, Emitter.Listener callback) {
        listenerMap.get(targetClientId).removeIf(listener ->
                listener.getCallback() == callback && listener.getEvent().equals(event));
    }

    private class Listener {
        private String event;
        private Emitter.Listener callback; // the original callback, this will NOT be executed
        private Emitter.Listener newCallback; // the modified callback, after remove the first arguments, this will be executed

        Listener(String event, Emitter.Listener callback, Emitter.Listener newCallback) {
            this.event = event;
            this.callback = callback;
            this.newCallback = newCallback;
        }

        Listener(Emitter.Listener callback, Emitter.Listener newCallback) {
            this.callback = callback;
            this.newCallback = newCallback;
        }

        String getEvent() {
            return event;
        }

        Emitter.Listener getCallback() {
            return callback;
        }

        Emitter.Listener getNewCallback() {
            return newCallback;
        }
    }
}
