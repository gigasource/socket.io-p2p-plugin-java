package io.gigasource.p2p_client.api;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.socket.client.Socket;

public class Core {
    private Socket socket;

    public Core(Socket socket) {
        this.socket = socket;
    }

    public void joinRoom(Object... args) {
        socket.emit(SocketEvent.JOIN_ROOM, args);
    }

    public void leaveRoom(Object... args) {
        socket.emit(SocketEvent.LEAVE_ROOM, args);
    }

    public void emitRoom(Object... args) {
        socket.emit(SocketEvent.EMIT_ROOM, args);
    }
}
