package io.gigasource.p2p_client.api;

import io.gigasource.p2p_client.constants.SocketEvent;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.util.ArrayList;
import java.util.List;

public class Service {
    private Socket socket;
    private Message messageApi;
    private List<String> subsccribedTopics;

    public Service(Socket socket, Message messageApi) {
        this.socket = socket;
        this.messageApi = messageApi;
        subsccribedTopics = new ArrayList<>();
    }

    public void emitService(String serviceName, String api, Object... args) {
        messageApi.emitTo(serviceName, api, args);
    }

    public void onService(String serviceName, String api, Emitter.Listener callback) {
        messageApi.from(serviceName).on(api, callback);
    }

    public void subscribeService(String service, String topicName, Emitter.Listener callback) {
        emitService(service, SocketEvent.SUBSCRIBE_TOPIC, messageApi.getClientId(), topicName,
                (Ack) (args) -> {
                    String modifiedTopicName = modifyTopicName(service, topicName);
                    if (!subsccribedTopics.contains(modifiedTopicName)) subsccribedTopics.add(modifiedTopicName);

                    String topicDefaultEvent = modifiedTopicName + "-" + SocketEvent.DEFAULT_TOPIC_EVENT;
                    String topicDestroyedEvent = modifiedTopicName + "-" + SocketEvent.TOPIC_BEING_DESTROYED;

                    socket.on(topicDefaultEvent, callback);
                    socket.once(topicDestroyedEvent, (args2) ->
                            socket.off(topicDefaultEvent, callback));
                });
    }

    public void unsubscribeTopic(String service, String topicName) {
        String modifiedTopicName = modifyTopicName(service, topicName);
        if (!subsccribedTopics.contains(modifiedTopicName)) return;

        subsccribedTopics.removeIf(topic -> topic.equals(topicName));
        socket.emit(service, SocketEvent.UNSUBSCRIBE_TOPIC, messageApi.getClientId(), topicName);

        String topicDefaultEvent = modifiedTopicName + "-" + SocketEvent.DEFAULT_TOPIC_EVENT;
        String topicDestroyedEvent = modifiedTopicName + "-" + SocketEvent.TOPIC_BEING_DESTROYED;

        socket.off(topicDefaultEvent);
        socket.off(topicDestroyedEvent);
    }

    private String modifyTopicName(String service, String topicName) {
        return "service:" + service + ":topic:" + topicName;
    }
}
