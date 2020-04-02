import io.gigasource.p2p_client.P2pClientPlugin;
import io.gigasource.p2p_client.api.Message;
import io.gigasource.p2p_client.constants.SocketEvent;
import io.gigasource.p2p_client.exception.TargetClientException;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("P2pClientPlugin Message API test")
class MessageApiTest {
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
    @DisplayName("Constructor")
    class Constructor {
        @Test
        @DisplayName("should listen to MULTI_API_TARGET_DISCONNECT event")
        void initTargetDisconnectListener() {
            assertEquals(1, client1.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());
            assertEquals(1, client2.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());
            assertEquals(1, client3.listeners(SocketEvent.MULTI_API_TARGET_DISCONNECT).size());
        }

        @Test
        @DisplayName("should listen to SERVER_ERROR event")
        void initServerErrorListener() {
            assertEquals(1, client1.listeners(SocketEvent.SERVER_ERROR).size());
            assertEquals(1, client2.listeners(SocketEvent.SERVER_ERROR).size());
            assertEquals(1, client3.listeners(SocketEvent.SERVER_ERROR).size());
        }
    }

    @Nested
    @DisplayName("addP2pTarget function")
    class AddP2pTarget {
        @Test
        @DisplayName("should trigger onAddP2pTarget on peers")
        void triggerOnP2pTargetOnPeer() throws TargetClientException {
            CompletableFuture<String> result1 = new CompletableFuture<>();
            CompletableFuture<String> result2 = new CompletableFuture<>();

            client3.onAddP2pTarget(result1::complete);
            client4.onAddP2pTarget(result2::complete);

            client1.addP2pTarget(client3.getClientId());
            client1.addP2pTarget(client4.getClientId());

            assertEquals(client1.getClientId(), result1.join());
            assertEquals(client1.getClientId(), result2.join());
        }

        @Test
        @DisplayName("should throw error if target is not registered to server")
        void throwErrorIfSocketNotFound() {
            assertThrows(TargetClientException.class, () -> client1.addP2pTarget("invalidId"));
        }

        @Test
        @DisplayName("should allow adding multiple targets")
        void allowAddingMultiTargets() throws TargetClientException {
            CompletableFuture<Integer> result1 = new CompletableFuture<>();
            CompletableFuture<Integer> result2 = new CompletableFuture<>();

            client3.onAddP2pTarget((targetClientId) -> result1.complete(123));
            client4.onAddP2pTarget((targetClientId) -> result2.complete(456));

            client1.addP2pTarget(client3.getClientId());
            client1.addP2pTarget(client4.getClientId());

            assertEquals(123, result1.join());
            assertEquals(456, result2.join());
        }
    }

    @Nested
    @DisplayName("onAddP2pTarget function")
    class OnAddP2pTarget {
        @Test
        @DisplayName("should be able to handle multiple clients")
        void handleMultipleClients() throws TargetClientException {
            CompletableFuture<Void> lock = new CompletableFuture<>();
            List<String> clientIds = new ArrayList<>();

            client4.onAddP2pTarget((targetClientId) -> {
                clientIds.add(targetClientId);

                if (clientIds.size() == 3) lock.complete(null);
            });

            client1.addP2pTarget(client4.getClientId());
            client2.addP2pTarget(client4.getClientId());
            client3.addP2pTarget(client4.getClientId());

            lock.join();
            assertTrue(clientIds.contains(client1.getClientId()));
            assertTrue(clientIds.contains(client2.getClientId()));
            assertTrue(clientIds.contains(client3.getClientId()));
        }
    }

    @Nested
    @DisplayName("onAny function")
    class OnAny {
        @Test
        @DisplayName("should be able to receive event from any clients")
        void receiveEventFromAnyClients() {
            CompletableFuture<Void> lock = new CompletableFuture<>();
            String event = "testEvent";
            AtomicInteger count = new AtomicInteger();
            AtomicInteger result = new AtomicInteger();

            client4.onAny(event, (args) -> {
                result.getAndAdd((int) args[0]);
                count.getAndIncrement();

                if (count.get() == 3) lock.complete(null);
            });

            client1.emitTo(client4.getClientId(), event, 6);
            client2.emitTo(client4.getClientId(), event, 13);
            client3.emitTo(client4.getClientId(), event, 100);

            lock.join();
            assertEquals(119, result.get());
        }
    }

    @Nested
    @DisplayName("onceAny function")
    class OnceAny {
        @Test
        @DisplayName("should only be triggered once")
        void triggeredOnce() throws InterruptedException {
            String event = "testEvent";

            Emitter.Listener mockListener = mock(Emitter.Listener.class);

            client4.onceAny(event, mockListener);
            assertEquals(1, client4.listeners(event).size());

            client1.emitTo(client4.getClientId(), event, 6);
            client2.emitTo(client4.getClientId(), event, 13);
            client3.emitTo(client4.getClientId(), event, 100);

            Thread.sleep(200);
            verify(mockListener, times(1)).call(anyInt());
            assertEquals(0, client4.listeners(event).size());
        }
    }

    @Nested
    @DisplayName("offAny function")
    class OffAny {
        @Test
        @DisplayName("should remove listeners of both onAny & onceAny")
        void removeBothKinds() throws InterruptedException {
            String event = "testEvent";

            Emitter.Listener mockListener1 = mock(Emitter.Listener.class);
            Emitter.Listener mockListener2 = mock(Emitter.Listener.class);
            Emitter.Listener mockListener3 = mock(Emitter.Listener.class);
            Emitter.Listener mockListener4 = mock(Emitter.Listener.class);

            client4.onAny(event, mockListener1);
            client4.onAny(event, mockListener2);
            client4.onceAny(event, mockListener3);
            client4.onceAny(event, mockListener4);
            assertEquals(4, client4.listeners(event).size());

            client4.offAny(event, mockListener1);
            client4.offAny(event, mockListener3);
            assertEquals(2, client4.listeners(event).size());

            client1.emitTo(client4.getClientId(), event);
            Thread.sleep(500);
            assertEquals(1, client4.listeners(event).size());

            client4.offAny(event);
            assertEquals(0, client4.listeners(event).size());

            verify(mockListener1, times(0)).call();
            verify(mockListener2, times(1)).call();
            verify(mockListener3, times(0)).call();
            verify(mockListener4, times(1)).call();
        }
    }

    @Nested
    @DisplayName("from function")
    class From {
        @Test
        @DisplayName("should return object of type Message")
        void returnMessageObject() {
            assertEquals(client1.from(client2.getClientId()).getClass(), Message.class);
        }
    }

    @Nested
    @DisplayName("on function")
    class On {
        @Test
        @DisplayName("should be able to receive data without onAddP2pTarget")
        void canReceiveDataWithoutOnAdd() throws InterruptedException {
            String event = "TestEv";
            String data1 = "hello from client1";
            double data2 = 3.14;
            int data3 = 123456;
            AtomicBoolean done = new AtomicBoolean(false);

            client2.from(client1.getClientId()).on(event, (args) -> {
                assertEquals(data1, args[0]);
                assertEquals(data2, args[1]);
                assertEquals(data3, args[2]);
                done.set(true);
            });

            client1.emitTo(client2.getClientId(), event, data1, data2, data3);
            Awaitility.await().until(done::get);
        }

        @Test
        @DisplayName("should be able to receive data from multiple targets")
        void canReceiveDataFromMultiTargets() {
            String event1 = "TestEv";
            String event2 = "random-event";
            String event3 = "something";

            String data1 = "hello from client1";
            double data2 = 321.654;
            int data3 = 123456;
            AtomicInteger count = new AtomicInteger();

            client4.from(client1.getClientId()).on(event1, (arg) -> {
                assertEquals(data1, arg[0]);
                count.getAndIncrement();
            });
            client4.from(client2.getClientId()).on(event2, (arg) -> {
                assertEquals(data2, arg[0]);
                count.getAndIncrement();
            });
            client4.from(client3.getClientId()).on(event3, (arg) -> {
                assertEquals(data3, arg[0]);
                count.getAndIncrement();
            });

            client3.emitTo(client4.getClientId(), event3, data3);
            client1.emitTo(client4.getClientId(), event1, data1);
            client2.emitTo(client4.getClientId(), event2, data2);
            Awaitility.await().until(() -> count.get() == 3);
        }
    }

    @Nested
    @DisplayName("off function")
    class Off {
        @Test
        @DisplayName("without callback parameter - should remove event listeners of selected target only")
        void withoutCallbackParam() {
            String event1 = "event1";
            String event2 = "anotherEvent";

            client1.from(client2.getClientId()).on(event1, (args) -> {
            });
            client1.from(client3.getClientId()).on(event1, (args) -> {
            });
            client1.from(client3.getClientId()).on(event2, (args) -> {
            });

            assertEquals(2, client1.listeners(event1).size());
            assertEquals(1, client1.listeners(event2).size());

            client1.from(client2.getClientId()).off(event1);

            assertEquals(1, client1.listeners(event1).size());
            assertEquals(1, client1.listeners(event2).size());

            client1.from(client3.getClientId()).off(event1);

            assertEquals(0, client1.listeners(event1).size());
            assertEquals(1, client1.listeners(event2).size());
        }

        @Test
        @DisplayName("with callback parameter - should remove correct callback")
        void withCallbackParam() {
            String event1 = "event1";
            String event2 = "anotherEvent";

            Emitter.Listener listener1 = args -> {
            };
            Emitter.Listener listener2 = args -> {
            };
            Emitter.Listener listener3 = args -> {
            };

            client1.from(client2.getClientId()).on(event1, listener1);
            client1.from(client3.getClientId()).on(event1, listener2);
            client1.from(client3.getClientId()).on(event2, listener3);

            assertEquals(2, client1.listeners(event1).size());
            assertEquals(1, client1.listeners(event2).size());

            client1.from(client2.getClientId()).off(event1, listener1);

            assertEquals(1, client1.listeners(event1).size());
            assertEquals(1, client1.listeners(event2).size());

            // wrong listener -> should remove nothing
            client1.from(client3.getClientId()).off(event1, listener1);
            client1.from(client3.getClientId()).off(event1, listener3);
            client1.from(client3.getClientId()).off(event2, listener1);
            client1.from(client3.getClientId()).off(event2, listener2);

            assertEquals(1, client1.listeners(event1).size());
            assertEquals(1, client1.listeners(event2).size());

            client1.from(client3.getClientId()).off(event1, listener2);

            assertEquals(0, client1.listeners(event1).size());
            assertEquals(1, client1.listeners(event2).size());
        }
    }

    @Nested
    @DisplayName("once function")
    class Once {
        @Test
        @DisplayName("should only be triggered once")
        void triggeredOnce() throws InterruptedException {
            String event = "eventToTest";
            Emitter.Listener mockListener = mock(Emitter.Listener.class);

            client4.from(client1.getClientId()).once(event, mockListener);

            client1.emitTo(client4.getClientId(), event, 16);
            client2.emitTo(client4.getClientId(), event, 1);
            client3.emitTo(client4.getClientId(), event, 34);

            assertEquals(1, client4.listeners(event).size());

            Thread.sleep(200);
            verify(mockListener, times(1)).call(anyInt());
            assertEquals(0, client4.listeners(event).size());
        }
    }

    @Nested
    @DisplayName("emitTo function")
    class EmitTo {
        @Test
        @DisplayName("should send data to correct target")
        void sendDataToCorrectTarget() {
            String eventName = "testEv";
            String payload1 = "testData1";
            int payload2 = 194;
            AtomicReference<Object> result1 = new AtomicReference<>();

            client3.from(client1.getClientId()).on(eventName, (args) -> result1.set(args[0]));

            // this should have no impact on the result
            client4.from(client1.getClientId()).on(eventName, (args) -> result1.set(args[0]));


            client1.emitTo(client3.getClientId(), eventName, payload1);
            client2.emitTo(client3.getClientId(), eventName, payload2); // this should have no impact on the result

            Awaitility.await().until(() -> result1.get() != null);
            assertEquals(payload1, result1.get());
        }

        @Test
        @DisplayName("should be able to send data with acknowledgement function")
        void testAck() {
            String eventName = "random-event";
            String payload = "Lorem ipsum";
            double ackPayload = 12.54;
            AtomicReference<Object> result1 = new AtomicReference<>();
            AtomicReference<Object> result2 = new AtomicReference<>();

            client2.from(client1.getClientId()).on(eventName, args -> {
                result1.set(args[0]);
                ((Ack) args[1]).call(ackPayload);
            });

            client1.emitTo(client2.getClientId(), eventName, payload, (Ack) args -> result2.set(args[0]));

            Awaitility.await().until(() -> result2.get() != null);
            assertEquals(payload, result1.get());
            assertEquals(ackPayload, result2.get());
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {
        @Test
        @DisplayName("when a client disconnects after using addP2pTarget function - related listeners should be removed from its peer")
        void removeRelatedListeners() throws TargetClientException, InterruptedException {
            String event1 = "testEventNo1";
            String event2 = "random-name";
            String event3 = "123-abc-ev";

            client3.onAddP2pTarget(targetClientId -> {
                client3.from(targetClientId).on(event1, (args) -> {});
                client3.from(targetClientId).on(event1, (args) -> {});
                client3.from(targetClientId).on(event1, (args) -> {});
                client3.from(targetClientId).on(event2, (args) -> {});
                client3.from(targetClientId).on(event3, (args) -> {});
            });

            client4.onAddP2pTarget(targetClientId -> {
                client4.from(targetClientId).on(event1, (args) -> {});
                client4.from(targetClientId).on(event2, (args) -> {});
            });

            client1.addP2pTarget(client3.getClientId());
            client1.addP2pTarget(client4.getClientId());
            client2.addP2pTarget(client3.getClientId());
            client2.addP2pTarget(client4.getClientId());

            assertEquals(6, client3.listeners(event1).size());
            assertEquals(2, client3.listeners(event2).size());
            assertEquals(2, client3.listeners(event3).size());

            assertEquals(2, client4.listeners(event1).size());
            assertEquals(2, client4.listeners(event2).size());
            client1.disconnect();

            Thread.sleep(200);

            assertEquals(3, client3.listeners(event1).size());
            assertEquals(1, client3.listeners(event2).size());
            assertEquals(1, client3.listeners(event3).size());

            assertEquals(1, client4.listeners(event1).size());
            assertEquals(1, client4.listeners(event2).size());
            client2.disconnect();

            Thread.sleep(200);

            assertEquals(0, client3.listeners(event1).size());
            assertEquals(0, client3.listeners(event2).size());
            assertEquals(0, client3.listeners(event3).size());

            assertEquals(0, client4.listeners(event1).size());
            assertEquals(0, client4.listeners(event2).size());
        }
    }
}
