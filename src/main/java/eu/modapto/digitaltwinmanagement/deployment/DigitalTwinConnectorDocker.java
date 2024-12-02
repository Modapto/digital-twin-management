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
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.fraunhofer.iosb.ilt.faaast.service.config.ServiceConfig;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.EnvironmentSerializationManager;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.SerializationException;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.http.HttpEndpointConfig;
import de.fraunhofer.iosb.ilt.faaast.service.filestorage.memory.FileStorageInMemoryConfig;
import de.fraunhofer.iosb.ilt.faaast.service.messagebus.internal.MessageBusInternalConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemoryConfig;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;


public class DigitalTwinConnectorDocker extends DigitalTwinConnector {

    private static final String CONTAINER_MODEL_FILE = "/app/model.aasx";
    private static final String CONTAINER_CONFIG_FILE = "/app/config.json";
    private static final int CONTAINER_PORT_INTERNAL = 8080;

    private final DockerClient dockerClient;
    private final DockerConfig dockerConfig;
    private final Path contextPath = Files.createTempDirectory("dt-context-files");
    private final File modelFile = contextPath.resolve("model.aasx").toFile();
    private final File configFile = contextPath.resolve("config.json").toFile();

    private String containerId;
    private boolean running = false;
    private boolean convertPaths;

    public DigitalTwinConnectorDocker(DigitalTwinConfig config, DockerConfig dockerConfig) throws Exception {
        super(config);
        this.dockerConfig = dockerConfig;
        dockerClient = DockerClientBuilder
                .getInstance(DefaultDockerClientConfig
                        .createDefaultConfigBuilder()
                        .withRegistryUsername("mjacoby")
                        .withRegistryPassword("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")
                        .withRegistryUrl(dockerConfig.getRegistry())
                        .build())
                .withDockerHttpClient(new ApacheDockerHttpClient.Builder()
                        .dockerHost(new URI(dockerConfig.getHost()))
                        .build())
                .build();
        dockerClient.pingCmd().exec();
        initContainer();
    }


    @Override
    public void start() throws Exception {
        if (running) {
            return;
        }
        CreateContainerResponse container = dockerClient.createContainerCmd(dockerConfig.getImage())
                .withExposedPorts(new ExposedPort(CONTAINER_PORT_INTERNAL))
                .withHostConfig(new HostConfig()
                        .withBinds(
                                new Bind(getHostFilename(modelFile), new Volume(CONTAINER_MODEL_FILE)),
                                new Bind(getHostFilename(configFile), new Volume(CONTAINER_CONFIG_FILE)))
                        .withPortBindings(
                                PortBinding.parse(String.format("%d:%d", config.getPort(), CONTAINER_PORT_INTERNAL))))
                .withEnv(
                        String.format("faaast_model=%s", CONTAINER_MODEL_FILE),
                        String.format("faaast_config=%s", CONTAINER_CONFIG_FILE),
                        "faaast_loglevel_faaast=TRACE")
                .exec();
        StartContainerCmd startCmd = dockerClient.startContainerCmd(container.getId());
        startCmd.exec();
        containerId = container.getId();
        running = true;
        System.out.println("Container started with ID: " + container.getId());
    }


    @Override
    public void stop() throws IOException {
        if (!running) {
            return;
        }
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        running = false;
    }


    private void initContainer() throws IOException, SerializationException {
        detectConvertPaths();
        writeConfigFile();
        writeModelFile();
    }


    private void detectConvertPaths() {
        Info info = dockerClient.infoCmd().exec();
        convertPaths = (Objects.nonNull(info.getKernelVersion()) && info.getKernelVersion().toLowerCase().contains("wsl"))
                || (System.getProperty("os.name", "").toLowerCase().startsWith("windows") && !info.getOsType().toLowerCase().startsWith("windows"));
    }


    private String getHostFilename(File file) {
        if (!convertPaths) {
            return file.getAbsolutePath();
        }
        return file.getAbsolutePath().replace("C:", "/mnt/c").replace("\\", "/");
    }


    private void writeConfigFile() throws IOException {
        ServiceConfig serviceConfig = ServiceConfig.builder()
                .endpoint(HttpEndpointConfig.builder()
                        .ssl(false)
                        .cors(true)
                        .port(CONTAINER_PORT_INTERNAL)
                        .build())
                .persistence(PersistenceInMemoryConfig.builder().build())
                .messageBus(MessageBusInternalConfig.builder().build())
                .fileStorage(FileStorageInMemoryConfig.builder().build())
                .assetConnections(config.getAssetConnections())
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
