package io.gigasource.p2p_client.example;

import io.gigasource.p2p_client.P2pClientPlugin;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Arrays;

public class ClientExample3 {
    public static void main(String[] args) {
        try {
            Socket socket = IO.socket("http://localhost:9000?clientId=D3");

            P2pClientPlugin p2pClientPlugin = P2pClientPlugin.createInstance(socket);

            p2pClientPlugin.connect();

            boolean connectionSuccess = p2pClientPlugin.registerP2pTarget("D2", null);
            System.out.println(p2pClientPlugin.getClientList());

            if (connectionSuccess) {
                p2pClientPlugin.emit2("testNoAckFromJava", "fromD3", "d3", "ddd3");
                p2pClientPlugin.emit2("testAckFromJava", "from Java D3", (Ack) args1 -> System.out.println(Arrays.toString(args1)));
                p2pClientPlugin.on("testFromTarget", args1 -> {
                    System.out.println(Arrays.toString(args1));
                });
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
