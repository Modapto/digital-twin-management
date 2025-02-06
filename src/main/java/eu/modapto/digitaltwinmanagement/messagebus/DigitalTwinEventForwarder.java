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

import de.fraunhofer.iosb.ilt.faaast.service.dataformat.json.JsonEventDeserializer;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.event.access.ExecuteEventMessage;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.event.access.OperationFinishEventMessage;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.event.access.OperationInvokeEventMessage;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceFinishedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceInvokedEvent;
import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceFinishedPayload;
import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceInvokedPayload;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DigitalTwinEventForwarder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalTwinEventForwarder.class);
    private static final String TOPIC_OPERATION_INVOKE = "events/OperationInvokeEventMessage";
    private static final String TOPIC_OPERATION_FINISH = "events/OperationFinishEventMessage";
    private final Map<Long, MqttClient> mqttClients;
    private final JsonEventDeserializer deserializer;

    @Autowired
    private KafkaBridge kafkaBridge;

    public DigitalTwinEventForwarder() {
        mqttClients = Collections.synchronizedMap(new HashMap<>());
        deserializer = new JsonEventDeserializer();
    }


    public void subscribeToDigitalTwin(Module module, String broker) {
        try {
            MqttClient client = new MqttClient(broker, UUID.randomUUID().toString().replace("-", ""), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            client.connect(options);
            client.setCallback(new MqttCallback() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    ExecuteEventMessage event = deserializer.read(new String(message.getPayload()), ExecuteEventMessage.class);
                    SmartService service = findServiceByAasOperation(module, event.getElement());
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
                        throw new RuntimeException(String.format("Receied unsupported message from Digital Twin message bus (type: %s)", event.getClass().getSimpleName()));
                    }
                }


                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}


                @Override
                public void connectionLost(Throwable cause) {}
            });
            client.subscribe(TOPIC_OPERATION_INVOKE);
            client.subscribe(TOPIC_OPERATION_FINISH);
            mqttClients.put(module.getId(), client);
        }
        catch (MqttException e) {
            throw new RuntimeException(String.format("Failed to subscribe to Digital Twin (reason: %s)", e.getMessage()), e);
        }
    }


    private SmartService findServiceByAasOperation(Module module, Reference reference) {
        return module.getServices().stream()
                .filter(x -> ReferenceHelper.equals(reference, x.getReference()))
                .findFirst()
                .orElse(null);
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


    public void unsubscribeFromDigitalTwin(Module module) {
        try {
            mqttClients.get(module.getId()).unsubscribe(TOPIC_OPERATION_INVOKE);
            mqttClients.get(module.getId()).unsubscribe(TOPIC_OPERATION_FINISH);
            mqttClients.get(module.getId()).disconnect();
            mqttClients.get(module.getId()).close();
        }
        catch (MqttException e) {
            LOGGER.warn("Failed to unsubscribe from Digital Twin (reason: %{})", e.getMessage(), e);
        }
        finally {
            mqttClients.remove(module.getId());
        }
    }

}
