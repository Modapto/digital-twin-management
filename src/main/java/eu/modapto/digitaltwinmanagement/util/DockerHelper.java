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
package eu.modapto.digitaltwinmanagement.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.config.DockerConfig;
import eu.modapto.digitaltwinmanagement.deployment.DeploymentType;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.RestBasedSmartService;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DockerHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerHelper.class);
    private static final String DEFAULT_TAG = "latest";
    private static DigitalTwinManagementConfig config;

    private DockerHelper() {}


    public static void setConfig(DigitalTwinManagementConfig dtConfig) {
        config = dtConfig;
    }


    public static DockerClient newClient() {
        return newClient(DockerConfig.builder().build());
    }


    public static DockerClient newClient(DockerConfig config) {
        try {
            DefaultDockerClientConfig.Builder clientConfig = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    //.withDockerTlsVerify(false)
                    .withRegistryUrl(config.getRegistryUrl());
            if (!StringHelper.isBlank(config.getRegistryUsername())) {
                clientConfig.withRegistryUsername(config.getRegistryUsername());
            }
            if (!StringHelper.isBlank(config.getRegistryPassword())) {
                clientConfig.withRegistryPassword(config.getRegistryPassword());
            }
            DockerClient result = DockerClientBuilder
                    .getInstance(clientConfig.build())
                    .withDockerHttpClient(new ApacheDockerHttpClient.Builder()
                            .dockerHost(new URI(config.getHost()))
                            .build())
                    .build();
            result.pingCmd().exec();
            return result;
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Error creating new docker client", e);
        }
    }


    private static void ensureImagePresent(DockerClient client, String imageName) {
        if (!imageExists(client, imageName)) {
            pullImage(client, imageName);
        }
    }


    private static void stopAndDeleteContainerByName(DockerClient client, String containerName) {
        findContainerByName(client, containerName)
                .ifPresent(x -> {
                    stopContainer(client, x);
                    removeContainer(client, x);
                });
    }


    private static Optional<String> findContainerByName(DockerClient client, String containerName) {
        if (Objects.isNull(containerName)) {
            return Optional.empty();
        }
        return client.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(containerName))
                .exec()
                .stream()
                .findFirst()
                .map(container -> container.getId());
    }


    public static String createContainer(DockerClient client, ContainerInfo containerInfo) {
        ensureImagePresent(client, containerInfo.getImageName());
        stopAndDeleteContainerByName(client, containerInfo.getContainerName());
        return client.createContainerCmd(containerInfo.getImageName())
                .withExposedPorts(containerInfo.getPortMappings().entrySet().stream()
                        .map(x -> new ExposedPort(x.getValue()))
                        .toList())
                .withHostConfig(new HostConfig()
                        .withBinds(containerInfo.getFileMappings().entrySet().stream()
                                .map(x -> new Bind(x.getKey().toString(), new Volume(x.getValue()), AccessMode.ro))
                                .toList())
                        .withPortBindings(containerInfo.getPortMappings().entrySet().stream()
                                .map(x -> PortBinding.parse(String.format("%d:%d", x.getKey(), x.getValue())))
                                .toList())
                        .withExtraHosts("host.docker.internal:host-gateway")
                        .withNetworkMode(getCurrentNetwork(client))
                        .withLinks(containerInfo.getLinkedContainers().entrySet().stream()
                                .map(x -> new Link(x.getKey(), x.getValue()))
                                .toList()))
                .withName(containerInfo.getContainerName())
                .withEnv(containerInfo.getEnvironmentVariables().entrySet().stream()
                        .map(x -> String.format("%s=%s", x.getKey(), x.getValue()))
                        .toList())
                .exec().getId();
    }


    public static void stopContainer(DockerClient dockerClient, String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
    }


    public static void removeContainer(DockerClient dockerClient, String containerId) {
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
    }


    public static void removeContainerByName(DockerClient dockerClient, String containerName) {
        findContainerByName(dockerClient, containerName).ifPresent(x -> removeContainer(dockerClient, x));
    }


    public static boolean isContainerRunning(DockerClient dockerClient, String containerId) {
        try {
            return dockerClient.inspectContainerCmd(containerId).exec().getState().getRunning();
        }
        catch (Exception e) {
            return false;
        }
    }


    private static boolean imageExists(DockerClient client, String image) {
        return client.listImagesCmd().exec().stream()
                .anyMatch(x -> x.getRepoTags() != null &&
                        Arrays.asList(x.getRepoTags()).contains(image));
    }


    private static void pullImage(DockerClient client, String image) {
        PullImageCmd pullImageCmd = client.pullImageCmd(image);
        try {
            pullImageCmd.start().awaitCompletion();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(String.format("failed to pull docker image from registry (image: %s, reason: %s)", image, e.getMessage()));
        }
    }


    public static String startContainer(DockerClient client,
                                        ContainerInfo containerInfo) {
        if (!imageExists(client, containerInfo.getImageName())) {
            pullImage(client, containerInfo.getImageName());
        }
        stopContainerIfRunningByName(client, containerInfo.getContainerName());
        removeContainerByName(client, containerInfo.getContainerName());
        String containerId = createContainer(client, containerInfo);
        client.startContainerCmd(containerId).exec();
        return containerId;
    }


    public static boolean stopContainerIfRunning(DockerClient client, String containerId) {
        if (isContainerRunning(client, containerId)) {
            client.removeContainerCmd(containerId).withForce(true).exec();
            return true;
        }
        return false;
    }


    public static boolean stopContainerIfRunningByName(DockerClient client, String containerName) {
        if (Objects.isNull(containerName)) {
            return false;
        }
        Optional<String> containerId = findContainerByName(client, containerName);
        if (containerId.isPresent()) {
            return stopContainerIfRunning(client, containerId.get());
        }
        return false;
    }


    public static String buildImage(DockerClient client, File dockerFile, String imageName) {
        BuildImageCmd buildImageCmd = client.buildImageCmd()
                .withDockerfile(dockerFile)
                .withTag(imageName);

        return buildImageCmd.exec(new BuildImageResultCallback() {
            @Override
            public void onNext(BuildResponseItem item) {
                super.onNext(item);
            }
        }).awaitImageId();
    }


    public static void publish(DockerClient client, String registry, String tag) {
        String targetTag = registry + "/" + tag;
        tag(client, tag, targetTag);
        client.pushImageCmd(targetTag)
                .exec(new PushImageResultCallback())
                .awaitSuccess();
    }


    public static void tag(DockerClient client, String image, String tag) {
        client.tagImageCmd(image, tag, DEFAULT_TAG).exec();
    }


    private static boolean pathConversionNeeded(DockerClient client) {
        Info info = client.infoCmd().exec();
        return (Objects.nonNull(info.getKernelVersion()) && info.getKernelVersion().toLowerCase().contains("wsl"))
                || (System.getProperty("os.name", "").toLowerCase().startsWith("windows") && !info.getOsType().toLowerCase().startsWith("windows"));
    }


    public void pushImage(DockerClient dockerClient, String imageName, String repository) throws InterruptedException {
        dockerClient.pushImageCmd(imageName)
                .withName(repository)
                .exec(new PushImageResultCallback() {
                    @Override
                    public void onNext(PushResponseItem item) {
                        super.onNext(item);
                    }
                }).awaitCompletion();
    }


    public static String getContainerName(Module module) {
        return String.format("modapto-module-%d", module.getId());
    }


    public static String getContainerName(RestBasedSmartService service) {
        return String.format("modapto-service-%d", service.getId());
    }


    public static String getCurrentContainerId() {
        return System.getenv("HOSTNAME");
    }


    public static String getCurrentNetwork(DockerClient client) {
        if (config.getDeploymentType() == DeploymentType.INTERNAL) {
            return null;
        }
        try {
            return client.listContainersCmd().withIdFilter(List.of(getCurrentContainerId())).exec()
                    .get(0).getNetworkSettings().getNetworks()
                    .keySet().iterator().next();
        }
        catch (Exception e) {
            LOGGER.debug("resolving current docker network failed", e);
        }
        return null;
    }

    @Getter
    @Setter
    @Builder
    public static class ContainerInfo {
        private String imageName;
        private String containerName;
        @Singular
        private Map<Integer, Integer> portMappings;
        @Singular
        private Map<File, String> fileMappings;
        @Singular
        private Map<String, String> environmentVariables;
        @Singular
        private Map<String, String> linkedContainers;
    }
}
