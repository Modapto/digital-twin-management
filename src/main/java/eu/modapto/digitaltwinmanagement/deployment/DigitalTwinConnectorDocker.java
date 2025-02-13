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
import de.fraunhofer.iosb.ilt.faaast.service.filestorage.filesystem.FileStorageFilesystemConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemoryConfig;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinDeploymentDockerConfig;
import eu.modapto.digitaltwinmanagement.model.InternalSmartService;
import eu.modapto.digitaltwinmanagement.util.DockerHelper;
import eu.modapto.digitaltwinmanagement.util.DockerHelper.ContainerInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DigitalTwinConnectorDocker extends DigitalTwinConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalTwinConnectorDocker.class);

    private static final int CONTAINER_HTTP_PORT_INTERNAL = 8080;
    private static final String CONTAINER_MODEL_FILE = "/app/model.json";
    private static final String CONTAINER_CONFIG_FILE = "/app/config.json";
    private static final String CONTAINER_FILE_STORGE_PATH = "/app/file-storage";
    private final Path contextPath = Files.createTempDirectory("dt-context-files");
    private final File modelFile = contextPath.resolve("model.json").toFile();
    private final File configFile = contextPath.resolve("config.json").toFile();
    private final Path fileStoragePath = contextPath.resolve("file-storage");
    private final DigitalTwinDeploymentDockerConfig dockerConfig;

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
                ContainerInfo.builder()
                        .imageName(dockerConfig.getImage())
                        .containerName(DockerHelper.getContainerName(config.getModule()))
                        .portMapping(config.getHttpPort(), CONTAINER_HTTP_PORT_INTERNAL)
                        .fileMapping(modelFile, CONTAINER_MODEL_FILE)
                        .fileMapping(configFile, CONTAINER_CONFIG_FILE)
                        .fileMapping(fileStoragePath.toFile(), CONTAINER_FILE_STORGE_PATH)
                        .environmentVariable("faaast_model", CONTAINER_MODEL_FILE)
                        .environmentVariable("faaast_config", CONTAINER_CONFIG_FILE)
                        .environmentVariable("faaast_loglevel_faaast", "TRACE")
                        .linkedContainers(config.getModule().getServices().stream()
                                .filter(InternalSmartService.class::isInstance)
                                .map(InternalSmartService.class::cast)
                                .collect(Collectors.toMap(
                                        x -> x.getContainerId(),
                                        x -> x.getName())))
                        .build());
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
        writeAuxiliaryFiles();
    }


    private void ensureDockerAvailable() {
        if (!dockerAvailable) {
            throw new RuntimeException("Deployment via docker not possible");
        }
    }


    private void writeConfigFile() throws IOException {
        ServiceConfig serviceConfig = ServiceConfig.builder()
                .endpoint(getHttpEndpointConfig(CONTAINER_HTTP_PORT_INTERNAL))
                .messageBus(getMessageBusMqttConfig())
                .submodelTemplateProcessor(getSimulationSubmodelTemplateProcessorConfig())
                .assetConnections(config.getAssetConnections())
                .persistence(PersistenceInMemoryConfig.builder().build())
                .fileStorage(FileStorageFilesystemConfig.builder()
                        .existingDataPath(CONTAINER_FILE_STORGE_PATH)
                        .build())
                .build();
        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .writeValue(configFile, serviceConfig);
    }


    private void writeModelFile() throws IOException, SerializationException {
        EnvironmentSerializationManager.serializerFor(DataFormat.JSON).write(modelFile, config.getEnvironmentContext());
    }


    private void writeAuxiliaryFiles() throws IOException, SerializationException {
        Files.createDirectory(fileStoragePath);
        for (var file: config.getEnvironmentContext().getFiles()) {
            Files.write(
                    fileStoragePath.resolve(file.getPath().startsWith("/")
                            ? file.getPath().substring(1)
                            : file.getPath()),
                    file.getFileContent());
        }
    }
}
