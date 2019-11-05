package io.gigasource.p2p_client;

import io.gigasource.p2p_client.api.Core;
import io.gigasource.p2p_client.api.object.stream.Duplex;
import io.gigasource.p2p_client.api.Message;
import io.gigasource.p2p_client.api.Stream;
import io.gigasource.p2p_client.exception.P2pStreamException;
import io.gigasource.p2p_client.exception.TargetClientException;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java9.util.function.Consumer;
import org.apache.commons.lang3.reflect.FieldUtils;


public class P2pClientPlugin extends Socket {
    private Core coreApi;
    private Message messageApi;
    private Stream streamApi;

    private P2pClientPlugin(Manager io, String nsp, Manager.Options opts, String clientId) {
        super(io, nsp, opts);

        messageApi = new Message(this, clientId);
        streamApi = new Stream(this, messageApi);
    }

    public static P2pClientPlugin createInstance(Socket socket, String clientId) {
        try {
            Manager io = socket.io();
            String nsp = (String) FieldUtils.readField(socket, "nsp", true);
            Manager.Options opts = (Manager.Options) FieldUtils.readField(io, "opts", true);

            return new P2pClientPlugin(io, nsp, opts, clientId);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Core API
    public void joinRoom(Object... args) {
        coreApi.joinRoom(args);
    }
    public void leaveRoom(Object... args) {
        coreApi.leaveRoom(args);
    }
    public void emitRoom(Object... args) {
        coreApi.emitRoom(args);
    }

    //todo: getClientList

    // Message API
    public void addP2pTarget(String targetClientId) throws TargetClientException {
        messageApi.addP2pTarget(targetClientId);
    }
    public void onAddP2pTarget(Consumer<String> callback) {
        messageApi.onAddP2pTarget(callback);
    }
    public Message from(String targetClientId) {
        return messageApi.from(targetClientId);
    }
    public void emitTo(String targetClientId, String event, Object... args) {
        messageApi.emitTo(targetClientId, event, args);
    }
    public String getClientId() { return messageApi.getClientId(); };
    public void onAny(String event, Emitter.Listener callback) {messageApi.onAny(event, callback);}
    public void onceAny(String event, Emitter.Listener callback) {messageApi.onceAny(event, callback);}
    public void offAny(String event, Emitter.Listener callback) {messageApi.offAny(event, callback);}
    public void offAny(String event) {messageApi.offAny(event, null);}

    // P2pMultiStreamAPI
    public Duplex addP2pStream(String targetClientId) throws P2pStreamException {
        return streamApi.addP2pStream(targetClientId);
    }
    public void onAddP2pStream(Consumer<Duplex> callback) {
        streamApi.onAddP2pStream(callback);
    }
    public void offAddP2pStream() {
        streamApi.offAddP2pStream();
    }
}
