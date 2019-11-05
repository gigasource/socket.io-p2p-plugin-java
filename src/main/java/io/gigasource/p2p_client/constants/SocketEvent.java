package io.gigasource.p2p_client.constants;

public class SocketEvent {
    // Core API events
    public static final String JOIN_ROOM = "JOIN_ROOM";
    public static final String LEAVE_ROOM = "LEAVE_ROOM";
    public static final String EMIT_ROOM = "EMIT_ROOM";

    // Message API events
    public static final String P2P_EMIT = "P2P_EMIT";
    public static final String P2P_EMIT_ACKNOWLEDGE = "P2P_EMIT_ACKNOWLEDGE";
    public static final String SERVER_ERROR = "SERVER_ERROR";

    // Stream API events
    public static final String P2P_REGISTER_STREAM = "P2P_REGISTER_STREAM";
    public static final String P2P_EMIT_STREAM = "P2P_EMIT_STREAM";
    public static final String STREAM_IDENTIFIER_PREFIX = "-from-stream-";
    public static final String PEER_STREAM_DESTROYED = "PEER_STREAM_DESTROYED";

    // Multi Messages API events
    public static final String MULTI_API_TARGET_DISCONNECT = "MULTI_API_TARGET_DISCONNECT";
    public static final String MULTI_API_ADD_TARGET = "MULTI_API_ADD_TARGET";

    // Multi Stream API events
    public static final String MULTI_API_CREATE_STREAM = "MULTI_API_CREATE_STREAM";

    // Service API events
    public static final String SUBSCRIBE_TOPIC = "SUBSCRIBE_TOPIC";
    public static final String UNSUBSCRIBE_TOPIC = "UNSUBSCRIBE_TOPIC";
    public static final String DEFAULT_TOPIC_EVENT = "DEFAULT_TOPIC_EVENT";
    public static final String TOPIC_BEING_DESTROYED = "TOPIC_BEING_DESTROYED";
}
