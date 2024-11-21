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
package eu.modapto.digitaltwinmanagement.deployment;

import de.fraunhofer.iosb.ilt.faaast.service.config.ServiceConfig;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.http.HttpEndpointConfig;
import de.fraunhofer.iosb.ilt.faaast.service.filestorage.memory.FileStorageInMemoryConfig;
import de.fraunhofer.iosb.ilt.faaast.service.messagebus.internal.MessageBusInternalConfig;
import de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemoryConfig;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import eu.modapto.digitaltwinmanagement.model.Module;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DigitalTwinManager {

    private static final String HOSTNAME = "localhost";
    private Map<Long, DigitalTwinConnector> instances = new HashMap<>();
    private final JsonDeserializer deserializer = new JsonDeserializer();
    @Autowired
    private DigitalTwinConnectorFactory connectorFactory;

    public URI deploy(Module module) throws Exception {
        if (instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module already exists (module id: %s)", module.getId()));
        }
        int port = findFreePort();
        DigitalTwinConnector dt = connectorFactory.create(
                module.getType(),
                buildServiceConfig(
                        deserializer.read(EncodingHelper.base64Decode(module.getAas()), Environment.class),
                        port));
        dt.start();
        instances.put(module.getId(), dt);
        return buildUri(port);
    }


    public void update(Module module) throws Exception {
        if (instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        // re-use old port - where to get it from?
        deploy(module);
    }


    public void undeploy(Module module) throws Exception {
        if (!instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        instances.remove(module.getId());
    }


    private static URI buildUri(int port) throws URISyntaxException {
        return URI.create(String.format("https://%s:%d", HOSTNAME, port));
    }


    private ServiceConfig buildServiceConfig(Environment model, int port) {
        return ServiceConfig.builder()
                .endpoint(HttpEndpointConfig.builder()
                        .cors(true)
                        .port(port)
                        .build())
                .persistence(PersistenceInMemoryConfig.builder()
                        .initialModel(model)
                        .build())
                .messageBus(MessageBusInternalConfig.builder().build())
                .fileStorage(FileStorageInMemoryConfig.builder().build())
                .build();
    }


    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
        catch (IOException e) {
            throw new RuntimeException("No free port found", e);
        }
    }
}
