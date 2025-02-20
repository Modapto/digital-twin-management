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

import de.fraunhofer.iosb.ilt.faaast.service.Service;
import de.fraunhofer.iosb.ilt.faaast.service.config.ServiceConfig;
import de.fraunhofer.iosb.ilt.faaast.service.exception.EndpointException;
import de.fraunhofer.iosb.ilt.faaast.service.exception.MessageBusException;
import de.fraunhofer.iosb.ilt.faaast.service.filestorage.memory.FileStorageInMemoryConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.exception.PersistenceException;
import de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemoryConfig;
import eu.modapto.digitaltwinmanagement.exception.DigitalTwinException;
import java.util.stream.Collectors;


public class DigitalTwinConnectorInternal extends DigitalTwinConnector {

    private final Service service;

    public DigitalTwinConnectorInternal(DigitalTwinConfig config) throws Exception {
        super(config);
        service = new Service(ServiceConfig.builder()
                .endpoint(getHttpEndpointConfig(config.getHttpPort()))
                .messageBus(getMessageBusMqttConfig())
                .submodelTemplateProcessor(getSimulationSubmodelTemplateProcessorConfig())
                .assetConnections(config.getAssetConnections())
                .persistence(PersistenceInMemoryConfig.builder()
                        .initialModel(config.getEnvironmentContext().getEnvironment())
                        .build())
                .fileStorage(FileStorageInMemoryConfig.builder()
                        .files(config.getEnvironmentContext().getFiles().stream()
                                .collect(Collectors.toMap(
                                        x -> x.getPath(),
                                        x -> x.getFileContent())))
                        .build())
                .build());
    }


    @Override
    public void start() {
        try {
            service.start();
        }
        catch (MessageBusException | EndpointException | PersistenceException e) {
            throw new DigitalTwinException(String.format("starting Digital Twin failed (reason: %s)", e.getMessage()), e);
        }
    }


    @Override
    public void stop() {
        service.stop();
    }
}
