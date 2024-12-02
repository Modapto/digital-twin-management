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

import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import eu.modapto.digitaltwinmanagement.model.EmbeddedSmartService;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.smartservice.embedded.EmbeddedSmartServiceHelper;
import eu.modapto.digitaltwinmanagement.util.Helper;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DigitalTwinManager {

    private static final String HOSTNAME = "localhost";
    private final Map<Long, DigitalTwinConnector> instances = new HashMap<>();
    @Autowired
    private DigitalTwinConnectorFactory connectorFactory;

    public void deploy(Module module) throws Exception {
        if (instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module already exists (module id: %s)", module.getId()));
        }
        deploy(module, findFreePort());
    }


    private void deploy(Module module, int port) throws Exception {
        createActualModel(module);
        DigitalTwinConnector dt = connectorFactory.create(
                module.getType(),
                DigitalTwinConfig.builder()
                        .environmentContext(module.getActualModel())
                        .port(port)
                        .assetConnections(module.getAssetConnections())
                        .build());
        dt.start();
        instances.put(module.getId(), dt);
        updateEndpoints(module, port);
    }


    private void updateEndpoints(Module module, int port) {
        module.setEndpoint(String.format("http://%s:%d/api/v3.0", HOSTNAME, port));
        for (var service: module.getServices()) {
            service.setEndpoint(module.getEndpoint() + service.getEndpoint());
        }
    }


    private void createActualModel(Module module) {
        EnvironmentContext actualModel = Helper.deepCopy(module.getProvidedModel());
        for (var service: module.getServices()) {
            if (service instanceof EmbeddedSmartService embedded) {
                EmbeddedSmartServiceHelper.addSmartService(actualModel, embedded);
            }
        }
        module.setActualModel(actualModel);
    }


    public void update(Module module) throws Exception {
        if (!instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        deploy(module, dt.config.getPort());
    }


    public void undeploy(Module module) throws Exception {
        if (!instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        module.setActualModel(null);
        instances.remove(module.getId());
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
