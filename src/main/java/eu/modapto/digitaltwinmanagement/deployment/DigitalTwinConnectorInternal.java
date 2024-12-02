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
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.http.HttpEndpointConfig;
import de.fraunhofer.iosb.ilt.faaast.service.filestorage.memory.FileStorageInMemoryConfig;
import de.fraunhofer.iosb.ilt.faaast.service.messagebus.internal.MessageBusInternalConfig;
import de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemoryConfig;
import eu.modapto.faaast.service.smt.simulation.SimulationSubmodelTemplateProcessorConfig;
import java.util.stream.Collectors;


public class DigitalTwinConnectorInternal extends DigitalTwinConnector {
    private final Service service;

    public DigitalTwinConnectorInternal(DigitalTwinConfig config) throws Exception {
        super(config);
        service = new Service(ServiceConfig.builder()
                .endpoint(HttpEndpointConfig.builder()
                        .cors(true)
                        .port(config.getPort())
                        .sni(false)
                        .ssl(false)
                        .build())
                .persistence(PersistenceInMemoryConfig.builder()
                        .initialModel(config.getEnvironmentContext().getEnvironment())
                        .build())
                .messageBus(MessageBusInternalConfig.builder().build())
                .fileStorage(FileStorageInMemoryConfig.builder()
                        .files(config.getEnvironmentContext().getFiles().stream()
                                .collect(Collectors.toMap(
                                        x -> x.getPath(),
                                        x -> x.getFileContent())))
                        .build())
                .submodelTemplateProcessor(new SimulationSubmodelTemplateProcessorConfig())
                .assetConnections(config.getAssetConnections())
                .build());
    }


    @Override
    public void start() throws Exception {
        service.start();
    }


    @Override
    public void stop() throws Exception {
        service.stop();
    }
}
