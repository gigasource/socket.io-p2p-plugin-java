import io.gigasource.p2p_client.P2pClientPlugin;
import io.gigasource.p2p_client.api.object.stream.Duplex;
import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.P2pStreamException;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P2pClientPlugin Stream API test")
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

    @BeforeEach
    void initClients() {
        client1 = createClient();
        client2 = createClient();
        client3 = createClient();
        client4 = createClient();

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @AfterEach
    void disconnectClients() {
        client1.disconnect();
        client2.disconnect();
        client3.disconnect();
        client4.disconnect();

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {
        @Test
        @DisplayName("should create a MULTI_API_CREATE_STREAM to refuse connection attempt")
        void createInitialEvent() {
            assertEquals(1, client2.listeners(SocketEvent.MULTI_API_CREATE_STREAM).size());
        }
    }

    @Nested
    @DisplayName("addP2pStream function")
    class AddP2pStream {
        @Test
        @DisplayName("should throw error if peer is not listening to add stream event")
        void shouldThrowError() {
            P2pStreamException e = assertThrows(P2pStreamException.class, () -> client1.addP2pStream(client2.getClientId()));
            assertEquals("Client is not listening to create stream event", e.getMessage());
        }

        @Test
        @DisplayName("should return a Duplex if peer is listening to add stream event")
        void shouldReturnDuplex() throws P2pStreamException {
            client2.onAddP2pStream(duplex -> {});
            Duplex duplex = client1.addP2pStream(client2.getClientId());
            assertEquals(Duplex.class, duplex.getClass());
        }
    }

    @Nested
    @DisplayName("onAddP2pStream function")
    class OnAddP2pStream {
        @Test
        @DisplayName("should remove default listener from constructor")
        void shouldRemoveDefaultListener() {
            Emitter.Listener initialListener, newListener;

            assertEquals(1, client2.listeners(SocketEvent.MULTI_API_CREATE_STREAM).size());
            initialListener = client2.listeners(SocketEvent.MULTI_API_CREATE_STREAM).get(0);
            newListener = client2.listeners(SocketEvent.MULTI_API_CREATE_STREAM).get(0);

            assertSame(initialListener, newListener);

            client2.onAddP2pStream(duplex -> {});
            assertEquals(1, client2.listeners(SocketEvent.MULTI_API_CREATE_STREAM).size());
            newListener = client2.listeners(SocketEvent.MULTI_API_CREATE_STREAM).get(0);

            assertNotSame(initialListener, newListener);
        }

        @Test
        @DisplayName("should remove old listeners if called more than once")
        void shouldRemoveOldListeners() {
            client2.onAddP2pStream(duplex -> {});
            client2.onAddP2pStream(duplex -> {});
            client2.onAddP2pStream(duplex -> {});

            assertEquals(1, client2.listeners(SocketEvent.MULTI_API_CREATE_STREAM).size());
        }

        @Test
        @DisplayName("should return a Duplex")
        void shouldReturnDuplex() throws P2pStreamException {
            AtomicReference<Duplex> duplex = new AtomicReference<>();

            client2.onAddP2pStream(duplex::set);

            client1.addP2pStream(client2.getClientId());

            Awaitility.await().until(() -> duplex.get() != null);
            assertEquals(Duplex.class, duplex.get().getClass());
        }
    }

    @Nested
    @DisplayName("the returned Duplex")
    class DuplexTest {
        @Test
        @DisplayName("should be destroyable (+ remove related listeners on destroyed)")
        void shouldBeDestroyable() throws P2pStreamException, InterruptedException {
            int originalCount1 = client1.listeners("disconnect").size();
            int originalCount2 = client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size();
            String sendDataEvent1;
            AtomicReference<String> sendDataEvent2 = new AtomicReference<>();

            client2.onAddP2pStream(duplex ->
                    sendDataEvent2.set(SocketEvent.P2P_EMIT_STREAM + SocketEvent.STREAM_IDENTIFIER_PREFIX + duplex.getTargetStreamId()));

            Duplex duplex = client1.addP2pStream(client2.getClientId());
            sendDataEvent1 = SocketEvent.P2P_EMIT_STREAM + SocketEvent.STREAM_IDENTIFIER_PREFIX + duplex.getTargetStreamId();

            assertEquals(originalCount1 + 1, client1.listeners("disconnect").size());
            assertEquals(originalCount1 + 1, client2.listeners("disconnect").size());
            assertEquals(originalCount2 + 1, client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());
            assertEquals(originalCount2 + 1, client2.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());
            assertEquals(1, client1.listeners(sendDataEvent1).size());
            assertEquals(1, client2.listeners(sendDataEvent2.get()).size());
            assertEquals(1, client1.listeners(SocketEvent.PEER_STREAM_DESTROYED).size());
            assertEquals(1, client2.listeners(SocketEvent.PEER_STREAM_DESTROYED).size());

            duplex.destroy();

            Thread.sleep(50);

            assertEquals(originalCount1, client1.listeners("disconnect").size());
            assertEquals(originalCount1, client2.listeners("disconnect").size());
            assertEquals(originalCount2, client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());
            assertEquals(originalCount2, client2.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());
            assertEquals(0, client1.listeners(sendDataEvent1).size());
            assertEquals(0, client2.listeners(sendDataEvent2.get()).size());
            assertEquals(0, client1.listeners(SocketEvent.PEER_STREAM_DESTROYED).size());
            assertEquals(0, client2.listeners(SocketEvent.PEER_STREAM_DESTROYED).size());
            assertTrue(duplex.isDestroyed());
        }

        @Test
        @DisplayName("should be able to transfer data to target client when connected")
        void shouldBeAbleToSendData() throws P2pStreamException, InterruptedException {
            Duplex duplex1;
            AtomicReference<Duplex> duplex2 = new AtomicReference<>();
            String testPayload = "Some random text to test duplex's send data feature";
            InputStream anyInputStream = new ByteArrayInputStream(testPayload.getBytes());
            OutputStream outputStream = new ByteArrayOutputStream();

            client2.onAddP2pStream(duplex2::set);
            duplex1 = client1.addP2pStream(client2.getClientId());

            duplex1.setInputStream(anyInputStream);
            duplex2.get().addOutputStream(outputStream);

            Thread.sleep(100);
            assertEquals(testPayload, outputStream.toString());
        }

        @Test
        @DisplayName("should not send data to wrong target")
        void shouldNotSendWrongTarget() throws P2pStreamException, InterruptedException {
            Duplex duplex1a, duplex1b, duplex3;
            AtomicReference<Duplex> duplex4 = new AtomicReference<>();
            String testPayload1a = "Some random text to test duplex's send data feature";
            String testPayload1b = "This should be the result";
            String testPayload3 = "This should not";

            InputStream inputStream1a = new ByteArrayInputStream(testPayload1a.getBytes());
            InputStream inputStream1b = new ByteArrayInputStream(testPayload1b.getBytes());
            InputStream inputStream3 = new ByteArrayInputStream(testPayload3.getBytes());
            OutputStream outputStream = new ByteArrayOutputStream();

            client4.onAddP2pStream(duplex4::set);
            duplex1a = client1.addP2pStream(client4.getClientId());
            duplex3 = client3.addP2pStream(client4.getClientId());
            duplex1b = client1.addP2pStream(client4.getClientId());

            duplex1a.setInputStream(inputStream1a);
            duplex1b.setInputStream(inputStream1b);
            duplex3.setInputStream(inputStream3);
            duplex4.get().addOutputStream(outputStream);

            Thread.sleep(100);
            assertEquals(testPayload1b, outputStream.toString());
        }

        @Test
        @DisplayName("should allow both ends to add stream")
        void shouldAllowBothEndsToAddStream() throws P2pStreamException {
            client2.onAddP2pStream(d -> {});
            Duplex duplex1 = client1.addP2pStream(client2.getClientId());
            assertEquals(Duplex.class, duplex1.getClass());

            client1.onAddP2pStream(d -> {});
            Duplex duplex2 = client2.addP2pStream(client1.getClientId());
            assertEquals(Duplex.class, duplex1.getClass());
            assertNotSame(duplex1, duplex2);
        }
    }

    @Nested
    @DisplayName("duplex lifecycle")
    class Lifecycle {
        @Test
        @DisplayName("should be destroyed on disconnecting (must work similarly on both ends, source & target)")
        void destroyedOnDisconnect() throws P2pStreamException, InterruptedException {
            AtomicReference<Duplex> duplex3 = new AtomicReference<>();
            AtomicReference<Duplex> duplex4 = new AtomicReference<>();
            Duplex duplex1, duplex2;

            client3.onAddP2pStream(duplex3::set);
            client4.onAddP2pStream(duplex4::set);

            duplex1 = client1.addP2pStream(client3.getClientId());
            duplex2 = client2.addP2pStream(client4.getClientId());

            assertFalse(duplex1.isDestroyed());
            assertFalse(duplex2.isDestroyed());
            assertFalse(duplex3.get().isDestroyed());
            assertFalse(duplex4.get().isDestroyed());

            client1.disconnect();
            Thread.sleep(50);

            assertTrue(duplex1.isDestroyed());
            assertFalse(duplex2.isDestroyed());
            assertTrue(duplex3.get().isDestroyed());
            assertFalse(duplex4.get().isDestroyed());

            client4.disconnect();
            Thread.sleep(50);

            assertTrue(duplex1.isDestroyed());
            assertTrue(duplex2.isDestroyed());
            assertTrue(duplex3.get().isDestroyed());
            assertTrue(duplex4.get().isDestroyed());
        }

        @Test
        @DisplayName("should be destroyed if peer stream is destroyed")
        void destroyedOnPeerDestroyed() throws P2pStreamException, InterruptedException {
            AtomicReference<Duplex> duplex2 = new AtomicReference<>();
            Duplex duplex1;

            client2.onAddP2pStream(duplex2::set);
            duplex1 = client1.addP2pStream(client2.getClientId());
            assertFalse(duplex1.isDestroyed());
            assertFalse(duplex2.get().isDestroyed());

            duplex2.get().destroy();
            Thread.sleep(50);
            assertTrue(duplex1.isDestroyed());
            assertTrue(duplex2.get().isDestroyed());
        }

        @Test
        @DisplayName("a new duplex should be created every time addP2pStream is used")
        void newDuplexCreated() throws P2pStreamException {
            AtomicReference<Duplex> duplex3 = new AtomicReference<>();
            AtomicReference<Duplex> duplex4 = new AtomicReference<>();
            Duplex duplex1, duplex2;

            client2.onAddP2pStream(duplex3::set);
            client2.onAddP2pStream(duplex4::set);

            duplex1 = client1.addP2pStream(client2.getClientId());
            duplex2 = client1.addP2pStream(client2.getClientId());

            assertNotSame(duplex1, duplex2);
            assertNotSame(duplex3, duplex4);
        }

        @Test
        @DisplayName("should remove listeners on disconnection/target disconnection")
        void disconnectionTest() throws P2pStreamException, InterruptedException {
            int originalCount1 = client1.listeners("disconnect").size();
            int originalCount2 = client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size();
            String sendDataEvent1, sendDataEvent2, sendDataEvent3;
            Duplex duplex1, duplex2, duplex3;
            AtomicReference<Duplex> duplex4 = new AtomicReference<>();

            client2.onAddP2pStream(duplex -> {});
            client3.onAddP2pStream(duplex -> {});
            client4.onAddP2pStream(duplex4::set);

            duplex1 = client1.addP2pStream(client2.getClientId());
            duplex2 = client1.addP2pStream(client3.getClientId());
            duplex3 = client1.addP2pStream(client4.getClientId());

            sendDataEvent1 = SocketEvent.P2P_EMIT_STREAM + SocketEvent.STREAM_IDENTIFIER_PREFIX + duplex1.getTargetStreamId();
            sendDataEvent2 = SocketEvent.P2P_EMIT_STREAM + SocketEvent.STREAM_IDENTIFIER_PREFIX + duplex2.getTargetStreamId();
            sendDataEvent3 = SocketEvent.P2P_EMIT_STREAM + SocketEvent.STREAM_IDENTIFIER_PREFIX + duplex3.getTargetStreamId();

            assertEquals(1, client1.listeners(sendDataEvent1).size());
            assertEquals(1, client1.listeners(sendDataEvent2).size());
            assertEquals(1, client1.listeners(sendDataEvent3).size());
            assertEquals(originalCount1 + 3, client1.listeners("disconnect").size());
            assertEquals(originalCount2 + 3, client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());

            client2.disconnect();
            Thread.sleep(50);

            assertEquals(0, client1.listeners(sendDataEvent1).size());
            assertEquals(1, client1.listeners(sendDataEvent2).size());
            assertEquals(1, client1.listeners(sendDataEvent3).size());
            assertEquals(originalCount1 + 2, client1.listeners("disconnect").size());
            assertEquals(originalCount2 + 2, client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());

            client3.disconnect();
            Thread.sleep(50);

            assertEquals(0, client1.listeners(sendDataEvent1).size());
            assertEquals(0, client1.listeners(sendDataEvent2).size());
            assertEquals(1, client1.listeners(sendDataEvent3).size());
            assertEquals(originalCount1 + 1, client1.listeners("disconnect").size());
            assertEquals(originalCount2 + 1, client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());

            client1.disconnect();
            Thread.sleep(50);

            assertEquals(0, client1.listeners(sendDataEvent1).size());
            assertEquals(0, client1.listeners(sendDataEvent2).size());
            assertEquals(0, client1.listeners(sendDataEvent3).size());
            assertEquals(originalCount1, client1.listeners("disconnect").size());
            assertEquals(originalCount2, client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());

            String sendDataEvent4 = SocketEvent.P2P_EMIT_STREAM + SocketEvent.STREAM_IDENTIFIER_PREFIX + duplex4.get().getTargetStreamId();
            assertEquals(0, client4.listeners(sendDataEvent4).size());
            assertEquals(originalCount1, client4.listeners("disconnect").size());
            assertEquals(originalCount2, client4.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());
        }
    }
}
