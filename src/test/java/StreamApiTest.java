import io.gigasource.p2p_client.P2pClientPlugin;
import io.gigasource.p2p_client.api.one_to_one_connection.Duplex;
import io.gigasource.p2p_client.constants.SocketEvent;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URISyntaxException;
import java.util.UUID;

import static org.junit.Assert.*;

public class StreamApiTest {
    private P2pClientPlugin client1, client2, client3, client4;
    private int numberOfClients = 4;

    private P2pClientPlugin createClient() {
        String clientId = UUID.randomUUID().toString();
        try {
            Socket socket = IO.socket("http://localhost:9000?clientId=" + clientId);
            P2pClientPlugin instance = P2pClientPlugin.createInstance(socket, clientId);
            instance.connect();

            return instance;
        } catch (URISyntaxException | NullPointerException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Before
    public void initClients() {
        client1 = createClient();
        client2 = createClient();
        client3 = createClient();
        client4 = createClient();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @After
    public void disconnectClients() {
        client1.disconnect();
        client2.disconnect();
        client3.disconnect();
        client4.disconnect();
    }

    @Test
    @Category(P2pStreamTest.class)
    public void shouldCreateRequiredSocketEventListeners() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);

        client2.onRegisterP2pStream(null);
        Duplex stream = client1.registerP2pStream();

        assertEquals(1, client1.listeners(SocketEvent.P2P_EMIT_STREAM).size());
        assertEquals(2, client1.listeners(SocketEvent.P2P_UNREGISTER).size());
        assertEquals(2, client1.listeners(SocketEvent.P2P_DISCONNECT).size());
        assertEquals(1, client1.listeners("disconnect").size());
    }

    @Test
    @Category(P2pStreamTest.class)
    public void shouldRemoveSocketEventListenersOnDestroy() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);

        client2.onRegisterP2pStream(null);
        Duplex stream = client1.registerP2pStream();
        stream.destroy();

        assertEquals(0, client1.listeners(SocketEvent.P2P_EMIT_STREAM).size());
        assertEquals(1, client1.listeners(SocketEvent.P2P_UNREGISTER).size());
        assertEquals(1, client1.listeners(SocketEvent.P2P_DISCONNECT).size());
        assertEquals(0, client1.listeners("disconnect").size());
    }

    private interface P2pStreamTest {
    }

    private interface Emit2FnTestNoAck {
    }

    private interface Emit2FnTestWithAck {
    }

    private interface GetClientListFnTest {
    }
}
