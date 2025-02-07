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
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.config.DockerConfig;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


public class DockerHelper {

    private static final String DEFAULT_TAG = "latest";

    public static DockerClient newClient() {
        return newClient(DockerConfig.builder().build());
    }


    public static DockerClient newClient(DockerConfig config) {
        try {
            DefaultDockerClientConfig.Builder clientConfig = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
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


    public static String createContainer(DockerClient client,
                                         String imageName,
                                         String containerName,
                                         Map<Integer, Integer> portMappings,
                                         Map<File, String> fileMappings,
                                         Map<String, String> environment) {
        ensureImagePresent(client, imageName);
        stopAndDeleteContainerByName(client, containerName);
        return client.createContainerCmd(imageName)
                .withExposedPorts(portMappings.entrySet().stream()
                        .map(x -> new ExposedPort(x.getValue()))
                        .toList())
                .withHostConfig(new HostConfig()
                        .withBinds(fileMappings.entrySet().stream()
                                .map(x -> new Bind(getHostFilename(client, x.getKey()), new Volume(x.getValue())))
                                .toList())
                        .withPortBindings(portMappings.entrySet().stream()
                                .map(x -> PortBinding.parse(String.format("%d:%d", x.getKey(), x.getValue())))
                                .toList()))
                .withName(containerName)
                .withEnv(environment.entrySet().stream()
                        .map(x -> String.format("%s=%s", x.getKey(), x.getValue()))
                        .toList())
                .exec().getId();
    }


    public static void startContainer(DockerClient dockerClient, String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
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


    public static String startContainer(DockerClient client,
                                        String image,
                                        Map<Integer, Integer> portMappings,
                                        Map<File, String> fileMappings,
                                        Map<String, String> environment) {
        return startContainer(client, image, null, portMappings, fileMappings, environment);
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
                                        String imageName,
                                        String containerName,
                                        Map<Integer, Integer> portMappings,
                                        Map<File, String> fileMappings,
                                        Map<String, String> environment) {
        if (!imageExists(client, imageName)) {
            pullImage(client, imageName);
        }
        stopContainerIfRunningByName(client, containerName);
        removeContainerByName(client, containerName);
        CreateContainerResponse container = client.createContainerCmd(imageName)
                .withExposedPorts(portMappings.entrySet().stream()
                        .map(x -> new ExposedPort(x.getValue()))
                        .toList())
                .withHostConfig(new HostConfig()
                        .withBinds(fileMappings.entrySet().stream()
                                .map(x -> new Bind(getHostFilename(client, x.getKey()), new Volume(x.getValue())))
                                .toList())
                        .withPortBindings(portMappings.entrySet().stream()
                                .map(x -> PortBinding.parse(String.format("%d:%d", x.getKey(), x.getValue())))
                                .toList()))
                .withName(Optional.ofNullable(containerName).orElse(UUID.randomUUID().toString()))
                .withEnv(environment.entrySet().stream()
                        .map(x -> String.format("%s=%s", x.getKey(), x.getValue()))
                        .toList())
                .exec();
        StartContainerCmd startCmd = client.startContainerCmd(container.getId());
        startCmd.exec();
        return container.getId();
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


    private static String getHostFilename(DockerClient client, File file) {
        if (!pathConversionNeeded(client)) {
            return file.getAbsolutePath();
        }
        return file.getAbsolutePath().replace("C:", "/mnt/c").replace("\\", "/");
    }


    private DockerHelper() {}


    /**
     * Creates a docker container using the provided image.
     *
     * @param client the docker client to use
     * @param imageName the name of the image
     * @return the container ID of the created container
     */
    public String createContainer(DockerClient client, String imageName) {
        ensureImagePresent(client, imageName);
        return client.createContainerCmd(imageName).exec().getId();
    }


    /**
     * Creates a docker container using the provided image and name.
     *
     * @param client the docker client to use
     * @param imageName the name of the image
     * @param containerName the name of the container
     * @return the container ID of the created container
     */
    public String createContainer(DockerClient client, String imageName, String containerName) {
        ensureImagePresent(client, imageName);
        stopAndDeleteContainerByName(client, containerName);
        return client.createContainerCmd(imageName)
                .withName(containerName)
                .exec().getId();
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


    public String startContainer(DockerClient dockerClient, String imageName, String containerName) {
        String containerId = createContainer(dockerClient, imageName, containerName);
        startContainer(dockerClient, containerId);
        return containerId;
    }
}
