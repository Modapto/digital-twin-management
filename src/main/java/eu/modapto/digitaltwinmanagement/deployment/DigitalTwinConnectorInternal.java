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
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.exception.DigitalTwinException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DigitalTwinConnectorInternal extends DigitalTwinConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalTwinConnectorInternal.class);
    private final Service service;

    public DigitalTwinConnectorInternal(DigitalTwinManagementConfig config, DigitalTwinConfig dtConfig) throws Exception {
        super(config, dtConfig);
        service = new Service(ServiceConfig.builder()
                .endpoint(getHttpEndpointConfig(dtConfig.getHttpPort()))
                .messageBus(getMessageBusMqttConfig())
                .submodelTemplateProcessor(getSimulationSubmodelTemplateProcessorConfig())
                .assetConnections(dtConfig.getAssetConnections())
                .persistence(PersistenceInMemoryConfig.builder()
                        .initialModel(dtConfig.getEnvironmentContext().getEnvironment())
                        .build())
                .fileStorage(FileStorageInMemoryConfig.builder()
                        .files(dtConfig.getEnvironmentContext().getFiles().stream()
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


    @Override
    public void recreate() {
        LOGGER.info("Recreating Digital Twin... (type: INTERNAL, moduleId: {})", dtConfig.getModule().getId());
        start();
    }
}
