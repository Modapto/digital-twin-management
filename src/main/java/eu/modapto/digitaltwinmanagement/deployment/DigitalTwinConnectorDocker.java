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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.dockerjava.api.DockerClient;
import de.fraunhofer.iosb.ilt.faaast.service.config.ServiceConfig;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.EnvironmentSerializationManager;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.SerializationException;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.http.HttpEndpointConfig;
import de.fraunhofer.iosb.ilt.faaast.service.filestorage.memory.FileStorageInMemoryConfig;
import de.fraunhofer.iosb.ilt.faaast.service.messagebus.mqtt.MessageBusMqttConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemoryConfig;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinDeploymentDockerConfig;
import eu.modapto.digitaltwinmanagement.util.DockerHelper;
import eu.modapto.dt.faaast.service.smt.simulation.SimulationSubmodelTemplateProcessorConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DigitalTwinConnectorDocker extends DigitalTwinConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalTwinConnectorDocker.class);
    private static final String CONTAINER_MODEL_FILE = "/app/model.aasx";
    private static final String CONTAINER_CONFIG_FILE = "/app/config.json";
    private static final int CONTAINER_PORT_INTERNAL = 8080;
    private static final int MESSAGEBUS_PORT_INTERNAL = 1883;

    private final DigitalTwinDeploymentDockerConfig dockerConfig;
    private final Path contextPath = Files.createTempDirectory("dt-context-files");
    private final File modelFile = contextPath.resolve("model.aasx").toFile();
    private final File configFile = contextPath.resolve("config.json").toFile();

    private DockerClient dockerClient;
    private String containerId;
    private boolean running = false;
    private boolean dockerAvailable = false;

    public DigitalTwinConnectorDocker(DigitalTwinConfig config, DigitalTwinDeploymentDockerConfig dockerConfig) throws Exception {
        super(config);
        this.dockerConfig = dockerConfig;
        try {
            dockerClient = DockerHelper.newClient(dockerConfig);
            dockerClient.pingCmd().exec();
            dockerAvailable = true;
        }
        catch (Exception e) {
            LOGGER.warn("Unable to connect to docker daemon. Requests to deploy Modules via docker will fail, internal deployment will work. (reason: {})", e.getMessage(), e);
        }
        initContainer();
    }


    @Override
    public void start() throws Exception {
        ensureDockerAvailable();
        if (running) {
            return;
        }
        containerId = DockerHelper.startContainer(
                dockerClient,
                dockerConfig.getImage(),
                Map.of(config.getPort(), CONTAINER_PORT_INTERNAL,
                        config.getMessageBusPort(), MESSAGEBUS_PORT_INTERNAL),
                Map.of(modelFile, CONTAINER_MODEL_FILE, configFile, CONTAINER_CONFIG_FILE),
                Map.of("faaast_model", CONTAINER_MODEL_FILE,
                        "faaast_config", CONTAINER_CONFIG_FILE,
                        "faaast_loglevel_faaast", "TRACE"));
        running = true;
        LOGGER.info("docker container started with ID {}", containerId);
    }


    @Override
    public void stop() throws IOException {
        if (!running) {
            return;
        }
        if (dockerAvailable) {
            DockerHelper.stopContainer(dockerClient, containerId);
            DockerHelper.removeContainer(dockerClient, containerId);
        }
        running = false;
    }


    private void initContainer() throws IOException, SerializationException {
        writeConfigFile();
        writeModelFile();
    }


    private void ensureDockerAvailable() {
        if (!dockerAvailable) {
            throw new RuntimeException("Deployment via docker not possible");
        }
    }


    private void writeConfigFile() throws IOException {
        ServiceConfig serviceConfig = ServiceConfig.builder()
                .endpoint(HttpEndpointConfig.builder()
                        .ssl(false)
                        .cors(true)
                        .port(CONTAINER_PORT_INTERNAL)
                        .build())
                .persistence(PersistenceInMemoryConfig.builder().build())
                .messageBus(MessageBusMqttConfig.builder()
                        .host("0.0.0.0")
                        .internal(true)
                        .port(MESSAGEBUS_PORT_INTERNAL)
                        .clientCertificate(null)
                        .serverCertificate(null)
                        .build())
                .fileStorage(FileStorageInMemoryConfig.builder().build())
                .assetConnections(config.getAssetConnections())
                .submodelTemplateProcessor(SimulationSubmodelTemplateProcessorConfig.builder().build())
                .build();
        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .writeValue(configFile, serviceConfig);
    }


    private void writeModelFile() throws IOException, SerializationException {
        EnvironmentSerializationManager.serializerFor(DataFormat.AASX).write(modelFile, config.getEnvironmentContext());
    }
}
