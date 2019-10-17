package io.gigasource.p2p_client;

import io.gigasource.p2p_client.api.many_to_many_connection.P2pMultiMessage;
import io.gigasource.p2p_client.api.many_to_many_connection.P2pMultiStream;
import io.gigasource.p2p_client.api.one_to_one_connection.Duplex;
import io.gigasource.p2p_client.api.one_to_one_connection.P2pMessage;
import io.gigasource.p2p_client.api.one_to_one_connection.P2pStream;
import io.socket.client.Manager;
import io.socket.client.Socket;
import java9.util.function.Consumer;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.util.List;

public class P2pClientPlugin extends Socket {
    private static P2pMessage p2pMessageApi;
    private static P2pStream p2pStreamApi;
    private static P2pMultiMessage p2pMultiMessageApi;
    private static P2pMultiStream p2pMultiStreamApi;

    private P2pClientPlugin(Manager io, String nsp, Manager.Options opts) {
        super(io, nsp, opts);
    }

    public static P2pClientPlugin createInstance(Socket socket, String clientId) {
        try {
            Manager io = socket.io();
            String nsp = (String) FieldUtils.readField(socket, "nsp", true);
            Manager.Options opts = (Manager.Options) FieldUtils.readField(io, "opts", true);

            P2pClientPlugin instance = new P2pClientPlugin(io, nsp, opts);

            p2pMessageApi = new P2pMessage(instance, clientId);
            p2pStreamApi = new P2pStream(instance, p2pMessageApi);
            p2pMultiMessageApi = new P2pMultiMessage(instance, clientId);
            p2pMultiStreamApi = new P2pMultiStream(instance, p2pMultiMessageApi);

            return instance;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    // P2pMessage API
    public void unregisterP2pTarget() {
        p2pMessageApi.unregisterP2pTarget();
    }
    public boolean registerP2pTarget(String targetClientId, String connectionOpts) {
        return p2pMessageApi.registerP2pTarget(targetClientId, connectionOpts);
    }
    public void emit2(String event, Object... args) {
        p2pMessageApi.emit2(event, args);
    }
    public List<String> getClientList() {
        return p2pMessageApi.getClientList();
    }
    public String getTargetClientId() {
        return p2pMessageApi.getTargetClientId();
    }
    public String getId() {
        return p2pMessageApi.getClientId();
    }

    // P2pStream API
    public Duplex registerP2pStream() {
        return p2pStreamApi.registerP2pStream();
    }
    public void onRegisterP2pStream(Consumer<Duplex> callback) {
        p2pStreamApi.onRegisterP2pStream(callback);
    }
    public void offRegisterP2pStream() {
        p2pStreamApi.offRegisterP2pStream();
    }

    // P2pMultiMessage API
    public void addP2pTarget(String targetClientId) {
        p2pMultiMessageApi.addP2pTarget(targetClientId);
    }
    public void onAddP2pTarget(Consumer<String> callback) {
        p2pMultiMessageApi.onAddP2pTarget(callback);
    }
    public P2pMultiMessage from(String targetClientId) {
        return p2pMultiMessageApi.from(targetClientId);
    }
    public void emitTo(String targetClientId, String event) {
        p2pMultiMessageApi.emitTo(targetClientId, event, (Object) null);
    }
    public void emitTo(String targetClientId, String event, Object[] args) {
        p2pMultiMessageApi.emitTo(targetClientId, event, args);
    }

    // P2pMultiStreamAPI
    public io.gigasource.p2p_client.api.many_to_many_connection.Duplex addP2pStream(String targetClientId) {
        return p2pMultiStreamApi.addP2pStream(targetClientId);
    }
    public void onAddP2pStream(Consumer<io.gigasource.p2p_client.api.many_to_many_connection.Duplex> callback) {
        p2pMultiStreamApi.onAddP2pStream(callback);
    }
    public void offAddP2pStream() {
        p2pMultiStreamApi.offAddP2pStream();
    }
}
