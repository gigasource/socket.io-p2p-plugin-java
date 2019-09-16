import io.gigasource.p2p_client.P2pClientPlugin;
import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.InvalidConnectionStateException;
import io.gigasource.p2p_client.exception.InvalidTargetClientException;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.junit.After;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

public class ClientPluginTest {
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
    }

    @After
    public void disconnectClients() {
        client1.disconnect();
        client2.disconnect();
        client3.disconnect();
        client4.disconnect();
    }

    @Test
    public void constructorShouldInitLifecycleListener() {
        assertEquals(1, client1.listeners(SocketEvent.P2P_REGISTER).size());
        assertEquals(1, client1.listeners(SocketEvent.P2P_DISCONNECT).size());
        assertEquals(1, client1.listeners(SocketEvent.P2P_UNREGISTER).size());

        assertEquals(1, client2.listeners(SocketEvent.P2P_REGISTER).size());
        assertEquals(1, client2.listeners(SocketEvent.P2P_DISCONNECT).size());
        assertEquals(1, client2.listeners(SocketEvent.P2P_UNREGISTER).size());

        assertEquals(1, client3.listeners(SocketEvent.P2P_REGISTER).size());
        assertEquals(1, client3.listeners(SocketEvent.P2P_DISCONNECT).size());
        assertEquals(1, client3.listeners(SocketEvent.P2P_UNREGISTER).size());
    }

    @Test
    @Category(RegisterP2pTargetFnTest.class)
    public void returnTrueIfSuccess() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
    }

    @Test
    @Category(RegisterP2pTargetFnTest.class)
    public void returnFalseIfIdInvalid() {
        boolean connectionSuccess = client1.registerP2pTarget("anInvalidId", null);
        assertFalse(connectionSuccess);
    }

    @Test
    @Category(RegisterP2pTargetFnTest.class)
    public void returnFalseIfTargetInConnection() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
        connectionSuccess = client3.registerP2pTarget(client2.getId(), null);
        assertFalse(connectionSuccess);
    }

    @Test(expected = InvalidTargetClientException.class)
    @Category(RegisterP2pTargetFnTest.class)
    public void throwExceptionIfTargetIsSource() {
        client1.registerP2pTarget(client1.getId(), null);
    }

    @Test(expected = InvalidConnectionStateException.class)
    @Category(RegisterP2pTargetFnTest.class)
    public void throwExceptionIfSourceClientInConnection() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
        client1.registerP2pTarget(client3.getId(), null);
    }

    // -----------------------------------------------------------------------------------------------

    @Test
    @Category(UnregisterP2pTargetFnTest.class)
    public void freeBothClients() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
        connectionSuccess = client3.registerP2pTarget(client2.getId(), null);
        assertFalse(connectionSuccess);

        client1.unregisterP2pTarget();
        assertNull(client1.getTargetClientId());
        assertNull(client2.getTargetClientId());

        connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
        connectionSuccess = client3.registerP2pTarget(client2.getId(), null);
        assertFalse(connectionSuccess);

        client2.unregisterP2pTarget(); // should work similarly with both clients
        assertNull(client1.getTargetClientId());
        assertNull(client2.getTargetClientId());

        connectionSuccess = client3.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
    }

    @Test
    @Category(UnregisterP2pTargetFnTest.class)
    public void clearListenersFromPreviousConnection() {
        boolean connectionSuccess = client1.registerP2pTarget(client3.getId(), null);
        assertTrue(connectionSuccess);

        connectionSuccess = client2.registerP2pTarget(client3.getId(), null);
        assertFalse(connectionSuccess);

        client1.unregisterP2pTarget();

        connectionSuccess = client2.registerP2pTarget(client3.getId(), null);
        assertTrue(connectionSuccess);

        connectionSuccess = client1.registerP2pTarget(client4.getId(), null);
        assertTrue(connectionSuccess);

        client1.unregisterP2pTarget();
        client1.disconnect();

        assertEquals(client3.getId(), client2.getTargetClientId());
        assertEquals(client2.getId(), client3.getTargetClientId());
    }

    @Test
    @Category(UnregisterP2pTargetFnTest.class)
    public void workSimilarlyFromBothSidesOfConnection() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
        assertEquals(client2.getId(), client1.getTargetClientId());
        assertEquals(client1.getId(), client2.getTargetClientId());
        client1.unregisterP2pTarget();
        assertNull(client1.getTargetClientId());
        assertNull(client2.getTargetClientId());

        connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
        assertEquals(client2.getId(), client1.getTargetClientId());
        assertEquals(client1.getId(), client2.getTargetClientId());
        client2.unregisterP2pTarget();
        assertNull(client1.getTargetClientId());
        assertNull(client2.getTargetClientId());
    }

    // -----------------------------------------------------------------------------------------------

    @Test
    @Category(Emit2FnTest.class)
    public void emitEventsToCorrectTarget() {
        try {
            CompletableFuture<Object> result1 = new CompletableFuture<>();
            CompletableFuture<Object> result2 = new CompletableFuture<>();
            CompletableFuture<Object> result3 = new CompletableFuture<>();

            String eventC1ToC2 = "1to2";
            String eventC1ToC3 = "1to3";
            String eventC3ToC2 = "3to2";

            String dataC1ToC2 = "from1to2";
            Boolean dataC1ToC3 = true;
            Integer dataC3ToC2 = 123321;

            client2.on(eventC1ToC2, args -> result1.complete(args[0]));

            client2.on(eventC3ToC2, args -> result2.complete(args[0]));

            client3.on(eventC1ToC3, args -> result3.complete(args[0]));

            boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
            assertTrue(connectionSuccess);
            client1.emit2(eventC1ToC2, dataC1ToC2);
            client1.unregisterP2pTarget();
            assertEquals(dataC1ToC2, result1.get());

            connectionSuccess = client3.registerP2pTarget(client1.getId(), null);
            assertTrue(connectionSuccess);
            client1.emit2(eventC1ToC3, dataC1ToC3);
            client1.unregisterP2pTarget();
            assertEquals(dataC1ToC3, result3.get());

            connectionSuccess = client3.registerP2pTarget(client2.getId(), null);
            assertTrue(connectionSuccess);
            client3.emit2(eventC3ToC2, dataC3ToC2);
            client2.unregisterP2pTarget();
            assertEquals(dataC3ToC2, result2.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test(expected = InvalidTargetClientException.class)
    @Category(Emit2FnTest.class)
    public void throwErrorIfTargetIdEmpty() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
        client1.unregisterP2pTarget();
        client1.emit2("testEvent", "testData");
    }

    @Test(expected = InvalidTargetClientException.class)
    @Category(Emit2FnTest.class)
    public void throwErrorIfEventNotSpecified() {
        boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
        assertTrue(connectionSuccess);
        client1.unregisterP2pTarget();
        client1.emit2(null, "testData");
    }

    @Test
    @Category(Emit2FnTestNoAck.class)
    public void ableToEmitEventWithArgs() {
        try {
            CompletableFuture<Integer> result1 = new CompletableFuture<>();
            CompletableFuture<Boolean> result2 = new CompletableFuture<>();
            CompletableFuture<String> result3 = new CompletableFuture<>();

            boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
            assertTrue(connectionSuccess);

            String eventName = "testEvent";
            Integer eventData1 = 456654;
            Boolean eventData2 = true;
            String eventData3 = "from client " + client1.getId();

            client2.on(eventName, args -> {
                result1.complete((Integer) args[0]);
                result2.complete((Boolean) args[1]);
                result3.complete((String) args[2]);
            });

            client1.emit2(eventName, eventData1, eventData2, eventData3);
            assertEquals(eventData1, result1.get());
            assertEquals(eventData2, result2.get());
            assertEquals(eventData3, result3.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    @Category(Emit2FnTestWithAck.class)
    public void ableToEmitEventWithArgsAndExecuteAck() {
        try {
            CompletableFuture<Integer> result1 = new CompletableFuture<>();
            CompletableFuture<Boolean> result2 = new CompletableFuture<>();
            CompletableFuture<String> result3 = new CompletableFuture<>();
            CompletableFuture<Double> result4 = new CompletableFuture<>();

            boolean connectionSuccess = client1.registerP2pTarget(client2.getId(), null);
            assertTrue(connectionSuccess);

            String eventName = "testEvent";
            Integer eventData1 = 456654;
            Boolean eventData2 = true;
            String eventData3 = "from client " + client1.getId();
            Double ackData = 7.234;

            client2.on(eventName, args -> {
                result1.complete((Integer) args[0]);
                result2.complete((Boolean) args[1]);
                result3.complete((String) args[2]);
                ((Ack) args[3]).call();
            });

            client1.emit2(eventName, eventData1, eventData2, eventData3, (Ack) args -> {
                result4.complete(ackData);
            });
            assertEquals(eventData1, result1.get());
            assertEquals(eventData2, result2.get());
            assertEquals(eventData3, result3.get());
            assertEquals(ackData, result4.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------------------------------

    @Test
    @Category(GetClientListFnTest.class)
    public void listAllConnectedClients() {
        List<String> clientList = client1.getClientList();
        assertEquals(numberOfClients, clientList.size());
        assertTrue(clientList.contains(client1.getId()));
        assertTrue(clientList.contains(client2.getId()));
        assertTrue(clientList.contains(client3.getId()));
        // Ensure that result should be the same with different clients
        clientList = client2.getClientList();
        assertEquals(numberOfClients, clientList.size());
        assertTrue(clientList.contains(client1.getId()));
        assertTrue(clientList.contains(client2.getId()));
        assertTrue(clientList.contains(client3.getId()));

        clientList = client3.getClientList();
        assertEquals(numberOfClients, clientList.size());
        assertTrue(clientList.contains(client1.getId()));
        assertTrue(clientList.contains(client2.getId()));
        assertTrue(clientList.contains(client3.getId()));
    }

    /* Problem: "disconnect" event is done on server side after getClientList is called, using Thread.sleep doesn't work
        because it blocks "disconnect" method logic in Java side -> need to have a way to "sleep" without blocking the current thread
    @Test
    @Category(GetClientListFnTest.class)
    public void updateChangesCorrectly() {
        client1.disconnect();
        List<String> clientList = client4.getClientList();
        assertEquals(numberOfClients - 1, clientList.size());
        assertFalse(clientList.contains(client1.getId()));

        client2.disconnect();
        clientList = client4.getClientList();
        assertEquals(numberOfClients - 2, clientList.size());
        assertFalse(clientList.contains(client2.getId()));

        client3.disconnect();
        clientList = client4.getClientList();
        assertEquals(numberOfClients - 3, clientList.size());
        assertFalse(clientList.contains(client3.getId()));
    }*/

    private interface RegisterP2pTargetFnTest {
    }

    private interface UnregisterP2pTargetFnTest {
    }

    private interface Emit2FnTest {
    }

    private interface Emit2FnTestNoAck {
    }

    private interface Emit2FnTestWithAck {
    }

    private interface GetClientListFnTest {
    }
}
