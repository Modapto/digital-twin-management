/*
 * Copyright (c) 2024 Fraunhofer IOSB, eine rechtlich nicht selbstaendige
 * Einrichtung der Fraunhofer-Gesellschaft zur Foerderung der angewandten
 * Forschung e.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.modapto.digitaltwinmanagement.messagebus;

import de.fraunhofer.iosb.ilt.faaast.service.dataformat.DeserializationException;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.json.JsonEventDeserializer;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.event.access.ExecuteEventMessage;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.event.access.OperationFinishEventMessage;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.event.access.OperationInvokeEventMessage;
import de.fraunhofer.iosb.ilt.faaast.service.util.PortHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.exception.ResourceNotFoundException;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceFinishedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceInvokedEvent;
import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceFinishedPayload;
import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceInvokedPayload;
import eu.modapto.digitaltwinmanagement.util.Processor;
import io.moquette.BrokerConstants;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptAcknowledgedMessage;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptConnectionLostMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.interception.messages.InterceptUnsubscribeMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DigitalTwinEventForwarder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalTwinEventForwarder.class);
    private static final Pattern REGEX_MQTT_TOPIC = Pattern.compile("module\\/(\\d{1,19})\\/Operation(Invoke|Finish)EventMessage");
    private final JsonEventDeserializer deserializer;
    private final Map<Long, Module> modules;
    private ExecutorService executorService;
    private Server mqttServer;
    @Getter
    private int mqttPort;
    private BlockingQueue<InterceptPublishMessage> eventQueue;

    @Autowired
    private DigitalTwinManagementConfig config;

    @Autowired
    private KafkaBridge kafkaBridge;

    public DigitalTwinEventForwarder() {
        deserializer = new JsonEventDeserializer();
        modules = Collections.synchronizedMap(new HashMap<>());
    }


    @PostConstruct
    public void init() {
        eventQueue = new ArrayBlockingQueue<>(config.getMqttQueueSize());
        executorService = Executors.newFixedThreadPool(config.getMqttThreadCount());
        for (int i = 0; i < config.getMqttThreadCount(); i++) {
            executorService.submit(new Processor<>(eventQueue, this::handle, "mqtt-consumer"));
        }
        mqttPort = config.getMqttPort() > 0 ? config.getMqttPort() : PortHelper.findFreePort();
        startMqttServer();
    }


    private void startMqttServer() {
        mqttServer = new Server();
        IConfig serverConfig = new MemoryConfig(new Properties());
        serverConfig.setProperty(BrokerConstants.IMMEDIATE_BUFFER_FLUSH_PROPERTY_NAME, String.valueOf(true));
        serverConfig.setProperty(BrokerConstants.PORT_PROPERTY_NAME, Integer.toString(mqttPort));
        serverConfig.setProperty(BrokerConstants.HOST_PROPERTY_NAME, config.getMqttHost());
        serverConfig.setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        LOGGER.debug("starting MQTT broker (port: {})", mqttPort);
        mqttServer.startServer(serverConfig, List.of(new MqttInterceptHandler()), null, null, null);
    }


    @PreDestroy
    public void tearDown() {
        if (Objects.nonNull(mqttServer)) {
            mqttServer.stopServer();
        }
        executorService.shutdown();
        try {
            if (executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                return;
            }
        }
        catch (InterruptedException ex) {
            LOGGER.error("Interrupted while waiting for shutdown.", ex);
            Thread.currentThread().interrupt();
        }
        List<Runnable> list = executorService.shutdownNow();
        LOGGER.warn("There were {} messages left on the MQTT queue", list.size());
    }


    public void subscribe(Module module) {
        modules.put(module.getId(), module);
    }


    public void unsubscribe(Module module) {
        modules.remove(module.getId());
    }


    private void handle(SmartService service, OperationInvokeEventMessage event) {
        kafkaBridge.publish(SmartServiceInvokedEvent.builder()
                .moduleId(service.getModule().getId())
                .payload(SmartServiceInvokedPayload.builder()
                        .inputArguments(event.getInput())
                        .serviceId(service.getId())
                        .invocationId(event.getInvocationId())
                        .name(service.getName())
                        .serviceCatalogId(service.getServiceCatalogId())
                        .endpoint(service.getOperationEndpoint())
                        .build())
                .build());
    }


    private void handle(SmartService service, OperationFinishEventMessage event) {
        kafkaBridge.publish(SmartServiceFinishedEvent.builder()
                .moduleId(service.getModule().getId())
                .payload(SmartServiceFinishedPayload.builder()
                        .outputArguments(event.getOutput())
                        .success(event.getSuccess())
                        .serviceId(service.getId())
                        .invocationId(event.getInvocationId())
                        .name(service.getName())
                        .serviceCatalogId(service.getServiceCatalogId())
                        .endpoint(service.getOperationEndpoint())
                        .build())
                .build());
    }


    private SmartService findServiceByAasOperation(long moduleId, Reference reference) {
        if (!modules.containsKey(moduleId)) {
            return null;
        }
        return modules.get(moduleId)
                .getServices().stream()
                .filter(x -> ReferenceHelper.equals(reference, x.getReference()))
                .findFirst()
                .orElse(null);
    }


    private void handle(InterceptPublishMessage msg) {
        LOGGER.trace("handling MQTT message (clientId: {}, topic: {}, payload: {})", msg.getClientID(), msg.getClientID(), msg.getPayload().toString(StandardCharsets.UTF_8));
        String topic = msg.getTopicName();
        Matcher matcher = REGEX_MQTT_TOPIC.matcher(topic);
        if (!matcher.matches()) {
            return;
        }
        String payload = msg.getPayload().toString(StandardCharsets.UTF_8);
        long moduleId = Long.parseLong(matcher.group(1));
        try {
            ExecuteEventMessage event = deserializer.read(payload, ExecuteEventMessage.class);
            SmartService service = findServiceByAasOperation(moduleId, event.getElement());
            if (Objects.isNull(service)) {
                return;
            }
            if (event instanceof OperationInvokeEventMessage invoke) {
                handle(service, invoke);
            }
            else if (event instanceof OperationFinishEventMessage finish) {
                handle(service, finish);
            }
            else {
                throw new RuntimeException(String.format("Received unsupported message from Digital Twin message bus (type: %s)", event.getClass().getSimpleName()));
            }
            LOGGER.trace("MQTT message handled (clientId: {}, topic: {}, payload: {})", msg.getClientID(), msg.getClientID(), msg.getPayload().toString(StandardCharsets.UTF_8));

        }
        catch (ResourceNotFoundException | DeserializationException e) {
            LOGGER.warn("error handling MQTT message from DT (reason: {})", e.getMessage(), e);
        }
    }

    private class MqttInterceptHandler extends AbstractInterceptHandler {

        @Override
        public void onPublish(InterceptPublishMessage msg) {
            if (!eventQueue.offer(msg)) {
                LOGGER.error("Failed to queue MQTT message");
            }
            LOGGER.trace("MQTT message queued (clientId: {}, topic: {}, payload: {})", msg.getClientID(), msg.getClientID(), msg.getPayload().toString(StandardCharsets.UTF_8));
        }


        @Override
        public void onConnect(InterceptConnectMessage msg) {
            LOGGER.trace("client connected (clientId: {})", msg.getClientID());
        }


        @Override
        public void onDisconnect(InterceptDisconnectMessage msg) {
            LOGGER.trace("client disconnected: (clientId: {})", msg.getClientID());
        }


        @Override
        public void onConnectionLost(InterceptConnectionLostMessage msg) {
            LOGGER.trace("connection lost: (clientId: {})", msg.getClientID());
        }


        @Override
        public void onSubscribe(InterceptSubscribeMessage msg) {
            LOGGER.trace("subscribe (clientId: {}, topic: {})", msg.getClientID(), msg.getTopicFilter());
        }


        @Override
        public void onUnsubscribe(InterceptUnsubscribeMessage msg) {
            LOGGER.trace("unsubscribe (clientId: {}, topic: {})", msg.getClientID(), msg.getTopicFilter());
        }


        @Override
        public void onMessageAcknowledged(InterceptAcknowledgedMessage msg) {
            LOGGER.trace("message acknowledged (topic: {}, packetId: {})", msg.getTopic(), msg.getPacketID());
        }


        @Override
        public String getID() {
            return "modapto-dt-management-mqtt-interceptor";
        }
    }
}
