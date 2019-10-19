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

public class ClientExample1 {
    public static void main(String[] args) {
        try {
            Socket socket = IO.socket("http://localhost:9000?clientId=D1");

            P2pClientPlugin p2pClientPlugin = P2pClientPlugin.createInstance(socket, "D1");

            p2pClientPlugin.connect();

            boolean connectionSuccess = p2pClientPlugin.registerP2pTarget("D2", null);
            System.out.println(p2pClientPlugin.getClientList());

            if (connectionSuccess) {
                JSONObject jsonObject = new JSONObject();
                JSONArray jsonArray = new JSONArray();
                jsonArray.put(1);
                jsonArray.put("2");
                jsonArray.put(3);

                jsonObject.put("test", 1);
                jsonObject.put("test2", "test");
                jsonObject.put("test3", new int[]{1, 2, 3, 4});
                jsonObject.put("test4", jsonArray);

                p2pClientPlugin.emit2("testObj", jsonObject, "2", 3, "4");
                p2pClientPlugin.emit2("testNoAckFromJava", "hello", "abc", "def");
                p2pClientPlugin.emit2("testAckFromJava", "from Java", (Ack) args1 -> System.out.println(Arrays.toString(args1)));
                p2pClientPlugin.on("testFromTarget", args1 -> {
                    System.out.println(Arrays.toString(args1));

                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    p2pClientPlugin.unregisterP2pTarget();
                    System.out.println("Unregister done");
                });
            }
        } catch (URISyntaxException | JSONException e) {
            e.printStackTrace();
        }
    }
}
