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
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.http.HttpEndpointConfig;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Objects;


public class DigitalTwinConnectorDocker extends DigitalTwinConnector {

    private final DockerClient dockerClient;
    private final int port;
    private File configFile;
    private String containerId;
    private DockerConfig dockerConfig;
    private boolean convertPaths;

    public DigitalTwinConnectorDocker(ServiceConfig config, DockerConfig dockerConfig) throws Exception {
        super(config);
        this.dockerConfig = dockerConfig;
        dockerClient = DockerClientBuilder
                .getInstance(DefaultDockerClientConfig
                        .createDefaultConfigBuilder()
                        //.withDockerHost(DOCKER_HOST)
                        //.withDockerTlsVerify(true)
                        //.withDockerCertPath("/home/user/.docker")
                        .withRegistryUsername("mjacoby")
                        .withRegistryPassword("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")
                        //.withRegistryEmail(registryMail)
                        .withRegistryUrl(dockerConfig.getRegistry())
                        .build())
                .withDockerHttpClient(new ApacheDockerHttpClient.Builder()
                        .dockerHost(new URI(dockerConfig.getHost()))
                        .build())
                .build();
        dockerClient.pingCmd().exec();
        port = config.getEndpoints().stream()
                .filter(HttpEndpointConfig.class::isInstance)
                .map(HttpEndpointConfig.class::cast)
                .findFirst()
                .orElseThrow()
                .getPort();
        detectConvertPaths();
        writeConfigFile(config);
    }


    private void detectConvertPaths() {
        Info info = dockerClient.infoCmd().exec();
        convertPaths = (Objects.nonNull(info.getKernelVersion()) && info.getKernelVersion().toLowerCase().contains("wsl"))
                || (System.getProperty("os.name", "").toLowerCase().startsWith("windows") && !info.getOsType().toLowerCase().startsWith("windows"));
    }


    private String getConfigFileOnDockerHost() {
        if (!convertPaths) {
            return configFile.getAbsolutePath();
        }
        return configFile.getAbsolutePath().replace("C:", "/mnt/c").replace("\\", "/");
    }


    private void writeConfigFile(ServiceConfig config) throws IOException {
        configFile = Files.createTempFile("modapto-dt-config", "").toFile();
        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .writeValue(configFile, config);
    }


    @Override
    public void start() throws Exception {
        String configPath = "/app/config.json";
        CreateContainerResponse container = dockerClient.createContainerCmd(dockerConfig.getImage())
                .withExposedPorts(new ExposedPort(port))
                .withHostConfig(new HostConfig()
                        .withBinds(new Bind(getConfigFileOnDockerHost(), new Volume(configPath)))
                        .withPortBindings(PortBinding.parse(String.format("%d:%d", port, port))))
                .withEnv(String.format("faaast_config=%s", configPath))
                .exec();
        StartContainerCmd startCmd = dockerClient.startContainerCmd(container.getId());
        startCmd.exec();
        containerId = container.getId();
        System.out.println("Container started with ID: " + container.getId());
    }


    @Override
    public void stop() throws IOException {
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        // TODO either close and then need to open on start() or keep open all the time        
        //docker.close();
    }

    //
    //    private void init() {
    //        dockerHost = env.getProperty("docker.host", DEFAULT_DOCKER_HOST);
    //        dockerRegistry = env.getProperty("docker.registry", DEFAULT_DOCKER_REGISTRY);
    //        dockerImage = env.getProperty("docker.image", DEFAULT_DOCKER_IMAGE);
    //    }


    public void setDockerConfig(DockerConfig dockerConfig) {
        this.dockerConfig = dockerConfig;
    }
}
