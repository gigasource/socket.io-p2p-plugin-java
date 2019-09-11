package io.gigasource.p2p_client.example;

import io.gigasource.p2p_client.P2pClientPlugin;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;

import java.net.URISyntaxException;
import java.util.Arrays;

public class ClientExample1 {
    public static void main(String[] args) {
        try {
            Socket socket = IO.socket("http://localhost:9000?clientId=D1");

            P2pClientPlugin p2pClientPlugin = P2pClientPlugin.createInstance(socket);

            p2pClientPlugin.connect();

            boolean connectionSuccess = p2pClientPlugin.registerP2pTarget("D2", null);

            if (connectionSuccess) {
                p2pClientPlugin.emit2("testNoAckFromJava", "hello", "abc", "def");
                p2pClientPlugin.emit2("testAckFromJava", "from Java", (Ack) args1 -> System.out.println(Arrays.toString(args1)));
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
