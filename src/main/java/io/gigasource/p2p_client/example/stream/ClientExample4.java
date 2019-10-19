package io.gigasource.p2p_client.example.stream;

import io.gigasource.p2p_client.P2pClientPlugin;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Arrays;

public class ClientExample4 {
    public static void main(String[] args) {
        try {
            Socket socket = IO.socket("http://localhost:9009?clientId=B");

            P2pClientPlugin p2pClientPlugin = P2pClientPlugin.createInstance(socket, "B");

            p2pClientPlugin.connect();

            p2pClientPlugin.onAddP2pTarget(System.out::println);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
