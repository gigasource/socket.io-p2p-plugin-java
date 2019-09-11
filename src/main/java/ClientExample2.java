import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;

import java.net.URISyntaxException;
import java.util.Arrays;

public class ClientExample2 {
    public static void main(String[] args) {
        try {
            Socket socket = IO.socket("http://localhost:9000?clientId=D2");
            P2pClientPlugin p2pClientPlugin = P2pClientPlugin.createInstance(socket);
            p2pClientPlugin.connect();

            p2pClientPlugin.on("testNoAckFromJava", (arguments) -> System.out.println(Arrays.toString(arguments)));

            p2pClientPlugin.on("testAckFromJava", (arguments) -> {
                Ack ack = (Ack) arguments[arguments.length - 1];

                ack.call("Ack returned");
                System.out.println(Arrays.toString(arguments));
            });
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
