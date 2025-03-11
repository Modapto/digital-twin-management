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
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.exception.DigitalTwinException;
import eu.modapto.digitaltwinmanagement.model.InternalSmartService;
import eu.modapto.digitaltwinmanagement.util.DockerHelper;
import eu.modapto.digitaltwinmanagement.util.DockerHelper.ContainerInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;


public class DigitalTwinConnectorDocker extends DigitalTwinConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalTwinConnectorDocker.class);

    public static final int CONTAINER_HTTP_PORT_INTERNAL = 8080;
    private static final String CONTAINER_MODEL_FILE = "/app/model.json";
    private static final String CONTAINER_CONFIG_FILE = "/app/config.json";
    private static final String CONTAINER_FILE_STORGE_PATH = "/app/file-storage";

    private static final Path TMP_DIR = Paths.get(System.getProperty("java.io.tmpdir") + "/dt-context");

    private Path contextPath;
    private File modelFile;
    private File configFile;
    private Path fileStoragePath;

    private DockerClient dockerClient;
    private boolean running = false;
    private boolean dockerAvailable = false;

    public DigitalTwinConnectorDocker(DigitalTwinManagementConfig config, DigitalTwinConfig dtConfig) {
        super(config, dtConfig);
        try {
            dockerClient = DockerHelper.newClient();
            dockerClient.pingCmd().exec();
            dockerAvailable = true;
        }
        catch (Exception e) {
            LOGGER.warn("Unable to connect to docker daemon. Requests to deploy Modules via docker will fail, internal deployment will work. (reason: {})", e.getMessage(), e);
        }
    }


    @Override
    public void start() {
        initContainer();
        ensureDockerAvailable();
        if (running) {
            return;
        }
        dtConfig.getModule().setContainerId(DockerHelper.startContainer(
                dockerClient,
                ContainerInfo.builder()
                        .imageName(config.getDtDockerImage())
                        .containerName(DockerHelper.getContainerName(dtConfig.getModule()))
                        .portMapping(dtConfig.getHttpPort(), CONTAINER_HTTP_PORT_INTERNAL)
                        .fileMapping(mapToHost(modelFile), CONTAINER_MODEL_FILE)
                        .fileMapping(mapToHost(configFile), CONTAINER_CONFIG_FILE)
                        .fileMapping(mapToHost(fileStoragePath.toFile()), CONTAINER_FILE_STORGE_PATH)
                        .environmentVariable("faaast_model", CONTAINER_MODEL_FILE)
                        .environmentVariable("faaast_config", CONTAINER_CONFIG_FILE)
                        .environmentVariable("faaast_loglevel_faaast", "TRACE")
                        .environmentVariable("faaast_show_stacktrace", "true")
                        .linkedContainers(dtConfig.getModule().getServices().stream()
                                .filter(InternalSmartService.class::isInstance)
                                .map(InternalSmartService.class::cast)
                                .collect(Collectors.toMap(
                                        x -> x.getContainerId(),
                                        x -> x.getName())))
                        .build()));
        DockerHelper.subscribeToLogs(dockerClient, dtConfig.getModule().getContainerId(), "module-" + dtConfig.getModule().getId());
        running = true;
        LOGGER.info("docker container started with ID {}", dtConfig.getModule().getContainerId());
    }


    @Override
    public void stop() {
        if (!running) {
            return;
        }
        if (dockerAvailable) {
            DockerHelper.unsubscribeFromLogs(dtConfig.getModule().getContainerId());
            DockerHelper.stopContainer(dockerClient, dtConfig.getModule().getContainerId());
            DockerHelper.removeContainer(dockerClient, dtConfig.getModule().getContainerId());
        }
        cleanUpTempDirectoryAndFiles();
        running = false;
    }


    private void initContainer() {
        if (initTempDirectoryAndFiles()) {
            writeConfigFile();
            writeModelFile();
            writeAuxiliaryFiles();
        }
    }


    private void cleanUpTempDirectoryAndFiles() {
        try {
            FileSystemUtils.deleteRecursively(contextPath);
        }
        catch (IOException ex) {
            LOGGER.debug("error cleaning up temp directory for docker-based DT (id: {}, dir: {})", dtConfig.getModule().getId(), contextPath);
        }
    }


    private boolean initTempDirectoryAndFiles() {
        boolean created = false;
        try {
            if (!Files.exists(TMP_DIR)) {
                Files.createDirectories(TMP_DIR);
            }
            contextPath = TMP_DIR.resolve("dt-" + dtConfig.getModule().getId());
            if (!Files.exists(contextPath)) {
                Files.createDirectories(contextPath);
                created = true;
            }
            modelFile = contextPath.resolve("model.json").toFile();
            configFile = contextPath.resolve("config.json").toFile();
            fileStoragePath = contextPath.resolve("file-storage");
        }
        catch (IOException e) {
            throw new DigitalTwinException("failed to initialize temp directory and files", e);
        }
        return created;
    }


    private void ensureDockerAvailable() {
        if (!dockerAvailable) {
            throw new DigitalTwinException("Deployment via docker not possible");
        }
    }


    private void writeConfigFile() {
        ServiceConfig serviceConfig = ServiceConfig.builder()
                .endpoint(getHttpEndpointConfig(CONTAINER_HTTP_PORT_INTERNAL))
                .messageBus(getMessageBusMqttConfig())
                .submodelTemplateProcessor(getSimulationSubmodelTemplateProcessorConfig())
                .assetConnections(dtConfig.getAssetConnections())
                .persistence(PersistenceInMemoryConfig.builder().build())
                .fileStorage(FileStorageFilesystemConfig.builder()
                        .existingDataPath(CONTAINER_FILE_STORGE_PATH)
                        .build())
                .build();
        try {
            new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                    .writeValue(configFile, serviceConfig);
        }
        catch (IOException e) {
            throw new DigitalTwinException("failed to serialize FA³ST config file", e);
        }
    }


    private void writeModelFile() {
        try {
            EnvironmentSerializationManager.serializerFor(DataFormat.JSON).write(modelFile, dtConfig.getEnvironmentContext());
        }
        catch (SerializationException | IOException e) {
            throw new DigitalTwinException("failed to serialize AAS model to file", e);
        }
    }


    private void writeAuxiliaryFiles() {
        try {
            Files.createDirectory(fileStoragePath);
            for (var file: dtConfig.getEnvironmentContext().getFiles()) {
                Files.write(
                        fileStoragePath.resolve(file.getPath().startsWith("/")
                                ? file.getPath().substring(1)
                                : file.getPath()),
                        file.getFileContent());
            }
        }
        catch (IOException e) {
            throw new DigitalTwinException("failed to write auxiliary files", e);
        }
    }


    private File mapToHost(File file) {
        if (StringHelper.isBlank(config.getDtDockerTmpDirHostMapping())) {
            return file;
        }
        try {
            Path hostDir = Path.of(config.getDtDockerTmpDirHostMapping());
            File result = new File(file.getAbsolutePath().replace(TMP_DIR.toString(), hostDir.toString()));
            LOGGER.debug("mapped path for docker (original: {}, mapped: {}, original (absolute): {}, mapped (absolute): {})",
                    file, result, file.getAbsolutePath(), result.getAbsolutePath());
            return result;
        }
        catch (InvalidPathException e) {
            LOGGER.warn("found invalid tmpDirHostMapping - will be ignored (tmpDirHostMapping: {}, error: {})", config.getDtDockerTmpDirHostMapping(), e);
            return file;
        }
    }


    @Override
    public void recreate() {
        initContainer();
        if (!DockerHelper.containerExists(dockerClient, dtConfig.getModule().getContainerId())) {
            LOGGER.info("Re-creating Digital Twin... (type: DOCKER, moduleId: {}, reason: container not running)", dtConfig.getModule().getId());
            start();
        }
        else if (!DockerHelper.isContainerRunning(dockerClient, dtConfig.getModule().getContainerId())) {
            LOGGER.info("Re-attachting to Digital Twin... (type: DOCKER, moduleId: {}, containerId: {})", dtConfig.getModule().getId(), dtConfig.getModule().getContainerId());
        }
    }
}
