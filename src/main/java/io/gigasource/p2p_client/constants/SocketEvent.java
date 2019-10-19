package io.gigasource.p2p_client.constants;

public class SocketEvent {
    // Message API events
    public static final String P2P_EMIT = "P2P_EMIT";
    public static final String P2P_EMIT_ACKNOWLEDGE = "P2P_EMIT_ACKNOWLEDGE";
    public static final String P2P_REGISTER = "P2P_REGISTER";
    public static final String P2P_DISCONNECT = "P2P_DISCONNECT";
    public static final String LIST_CLIENTS = "LIST_CLIENTS";
    public static final String P2P_UNREGISTER = "P2P_UNREGISTER";
    public static final String SERVER_ERROR = "SERVER_ERROR";

    // Stream API events
    public static final String P2P_REGISTER_STREAM = "P2P_REGISTER_STREAM";
    public static final String P2P_EMIT_STREAM = "P2P_EMIT_STREAM";

    // Multi Messages API events
    public static final String MULTI_API_TARGET_DISCONNECT = "MULTI_API_TARGET_DISCONNECT";
    public static final String MULTI_API_ADD_TARGET = "MULTI_API_ADD_TARGET";

    // Multi Stream API events
    public static final String MULTI_API_CREATE_STREAM = "MULTI_API_CREATE_STREAM";
}
