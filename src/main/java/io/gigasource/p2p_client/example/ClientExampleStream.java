package io.gigasource.p2p_client.example;

import io.gigasource.p2p_client.P2pClientPlugin;
import io.gigasource.p2p_client.lib.P2pStream;
import io.socket.client.IO;
import io.socket.client.Socket;

import java.io.IOException;
import java.net.URISyntaxException;

public class ClientExampleStream {
    public static void main(String[] args) throws IOException {
        try {
            Socket socket = IO.socket("http://localhost:9000?clientId=A");

            P2pClientPlugin p2pClientPlugin = P2pClientPlugin.createInstance(socket);

            P2pStream duplex = new P2pStream(p2pClientPlugin);
            duplex.addOutputStream(System.out);
            duplex.setInputStream(System.in);

            p2pClientPlugin.connect();
            boolean success = p2pClientPlugin.registerP2pTarget("B", null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }


//        byte[] a = "helloaaa".getBytes();
//        ByteArrayOutputStream stream = new ByteArrayOutputStream();
//        int b = 0;
//
//        try {
//            stream.write(a);
//            stream.writeTo(System.out);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

//        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
//        char c;
//        while ((c = ((char) r.read())) != ((char) -1)) {
//            System.out.println("test" + c);
//        }
//        System.out.println("end");
    }
}
