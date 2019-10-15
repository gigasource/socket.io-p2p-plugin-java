package io.gigasource.p2p_client.example.stream;

import io.gigasource.p2p_client.P2pClientPlugin;
import io.socket.client.IO;
import io.socket.client.Socket;

import java.net.URISyntaxException;

public class Client2 {
    public static void main(String[] args) {
        try {
            String clientId = "D";

            Socket socket = IO.socket("http://localhost:9000?clientId=" + clientId);
            P2pClientPlugin p2pClientPlugin = P2pClientPlugin.createInstance(socket, clientId);
            p2pClientPlugin.connect();

            p2pClientPlugin.onAddP2pStream(duplex -> {
                duplex.addOutputStream(System.out);
                return null;
            });
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
