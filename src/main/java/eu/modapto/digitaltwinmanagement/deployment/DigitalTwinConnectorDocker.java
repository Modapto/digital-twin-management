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
import com.github.dockerjava.api.model.RestartPolicy;
import de.fraunhofer.iosb.ilt.faaast.service.config.ServiceConfig;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.EnvironmentSerializationManager;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.SerializationException;
import de.fraunhofer.iosb.ilt.faaast.service.filestorage.filesystem.FileStorageFilesystemConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemoryConfig;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.exception.DigitalTwinException;
import eu.modapto.digitaltwinmanagement.exception.DockerException;
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
    private static final String CONTAINER_MOUNT_PATH = "/app/mount";
    private static final String CONTAINER_MODEL_FILE = "/app/mount/model.json";
    private static final String CONTAINER_CONFIG_FILE = "/app/mount/config.json";
    private static final String CONTAINER_FILE_STORGE_PATH = "/app/mount/file-storage";

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
        LOGGER.debug("starting DT (id: {})", dtConfig.getModule().getId());
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
                        .mountPathSrc(contextPath.toAbsolutePath().toString())
                        .mountPathDst(CONTAINER_MOUNT_PATH)
                        .environmentVariable("faaast_model", CONTAINER_MODEL_FILE)
                        .environmentVariable("faaast_config", CONTAINER_CONFIG_FILE)
                        .environmentVariable("faaast_loglevel_faaast", "TRACE")
                        .environmentVariable("faaast_show_stacktrace", "true")
                        .restartPolicy(RestartPolicy.parse(config.getDtRestartPolicy()))
                        .label("modapto-type", "module")
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
        LOGGER.debug("stopping module... (moduleId: {})", dtConfig.getModule().getId());
        if (!running) {
            LOGGER.debug("module already stopped (moduleId: {})", dtConfig.getModule().getId());
            return;
        }
        if (dockerAvailable) {
            DockerHelper.unsubscribeFromLogs(dtConfig.getModule().getContainerId());
            DockerHelper.stopContainer(dockerClient, dtConfig.getModule().getContainerId());
            DockerHelper.removeContainer(dockerClient, dtConfig.getModule().getContainerId());
            DockerHelper.removeVolume(dockerClient, DockerHelper.getVolume(dtConfig.getModule()));
        }
        cleanUpTempDirectoryAndFiles();
        LOGGER.debug("module stopped (moduleId: {})", dtConfig.getModule().getId());
        running = false;
    }


    private void initContainer() {
        LOGGER.debug("initializing DT container... (moduleId: {})", dtConfig.getModule().getId());
        initTempDirectoryAndFiles();
        writeConfigFile();
        writeModelFile();
        writeAuxiliaryFiles();
        LOGGER.debug("DT container initialized (moduleId: {})", dtConfig.getModule().getId());
    }


    private void cleanUpTempDirectoryAndFiles() {
        try {
            LOGGER.debug("deleting temp directory... (dir: {})", contextPath);
            FileSystemUtils.deleteRecursively(contextPath);
            LOGGER.debug("temp directory deleted (dir: {})", contextPath);
        }
        catch (IOException ex) {
            LOGGER.debug("deleting temp directory for docker-based DT failed (id: {}, dir: {})", dtConfig.getModule().getId(), contextPath);
        }
    }


    private boolean initTempDirectoryAndFiles() {
        LOGGER.debug("creating temp directory... (moduleId: {})", dtConfig.getModule().getId());
        boolean created = false;
        try {
            if (!Files.exists(TMP_DIR)) {
                Files.createDirectories(TMP_DIR);
                LOGGER.debug("created directory {}", TMP_DIR);
            }
            contextPath = TMP_DIR.resolve("dt-" + dtConfig.getModule().getId());
            if (!Files.exists(contextPath)) {
                Files.createDirectories(contextPath);
                LOGGER.debug("created directory {}", contextPath);
                created = true;
            }
            modelFile = contextPath.resolve("model.json").toFile();
            configFile = contextPath.resolve("config.json").toFile();
            fileStoragePath = contextPath.resolve("file-storage");
            LOGGER.debug("using temp files/directories: modelFile={}, configFile={}, fileStoragePath={}", modelFile, configFile, fileStoragePath);
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
        LOGGER.debug("writing config file...");
        ServiceConfig serviceConfig = ServiceConfig.builder()
                .core(getCoreConfig())
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
        LOGGER.debug("writing auxiliary files...");
        try {
            if (!fileStoragePath.toFile().exists()) {
                LOGGER.debug("creating directory... {}", fileStoragePath);
                Files.createDirectory(fileStoragePath);
                LOGGER.debug("directory created {}", fileStoragePath);
            }
        }
        catch (IOException e) {
            throw new DigitalTwinException(String.format(
                    "failed to create auxiliary file directory (%s)", fileStoragePath),
                    e);
        }
        for (var file: dtConfig.getEnvironmentContext().getFiles()) {
            try {
                Path path = fileStoragePath.resolve(file.getPath().startsWith("/")
                        ? file.getPath().substring(1)
                        : file.getPath());
                LOGGER.debug("writing auxiliary file {}", path);
                Files.createDirectories(path.getParent());
                if (path.toFile().exists()) {

                }
                Files.write(path, file.getFileContent());
                LOGGER.debug("auxiliary file written {}", path);
            }
            catch (IOException | InvalidPathException e) {
                LOGGER.error("failed to write auxiliary file '{}'", file.getPath(), e);
            }
        }

    }


    @Override
    public void recreate() {
        LOGGER.info("Re-creating Digital Twin... (type: DOCKER, moduleId: {})", dtConfig.getModule().getId());
        if (DockerHelper.containerExists(dockerClient, dtConfig.getModule().getContainerId())) {
            if (DockerHelper.isContainerRunning(dockerClient, dtConfig.getModule().getContainerId())) {
                LOGGER.info("Found existing running docker container for Digital Twin - should be re-attached automatically (type: DOCKER, moduleId: {}, containerId: {})",
                        dtConfig.getModule().getId(),
                        dtConfig.getModule().getContainerId());
                return;
            }
            LOGGER.info("Found existing stopped docker container for Digital Twin - attempting to start container and re-attach... (type: DOCKER, moduleId: {}, containerId: {})",
                    dtConfig.getModule().getId(),
                    dtConfig.getModule().getContainerId());
            try {
                DockerHelper.startContainer(dockerClient, dtConfig.getModule().getContainerId());
            }
            catch (DockerException e) {
                LOGGER.info(
                        "Restarting existing docker container for Digital Twin failed - container will be deleted and re-created (type: DOCKER, moduleId: {}, containerId: {})",
                        dtConfig.getModule().getId(),
                        dtConfig.getModule().getContainerId());
                DockerHelper.removeContainer(dockerClient, dtConfig.getModule().getContainerId());
                DockerHelper.removeVolume(dockerClient, DockerHelper.getVolume(dtConfig.getModule()));
            }
        }
        else {
            LOGGER.info(
                    "No existing docker container found for Digital Twin - container will be re-created (type: DOCKER, moduleId: {}, containerId: {})",
                    dtConfig.getModule().getId(),
                    dtConfig.getModule().getContainerId());
        }
        start();
        LOGGER.info(
                "Digital Twin re-created successfully (type: DOCKER, moduleId: {}, containerId: {})",
                dtConfig.getModule().getId(),
                dtConfig.getModule().getContainerId());
    }
}
