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
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.github.dockerjava.core.util.CompressArchiveUtil;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.exception.DockerException;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.RestBasedSmartService;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DockerHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerHelper.class);
    private static final Logger LOGGER_DOCKER = LoggerFactory.getLogger("Docker");
    private static final String DEFAULT_TAG = "latest";
    private static final Map<String, ResultCallback.Adapter<Frame>> loggingCallbacks = new ConcurrentHashMap<>();
    private static final String TEMP_CONTAINER_IMAGE = "busybox:1.37.0";
    private static DigitalTwinManagementConfig config;

    private DockerHelper() {}


    public static void setConfig(DigitalTwinManagementConfig dtConfig) {
        config = dtConfig;
    }


    public static DockerClient newClient() {
        try {
            DefaultDockerClientConfig.Builder clientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();
            if (!StringHelper.isBlank(config.getDockerRegistryUrl())) {
                clientConfigBuilder.withRegistryUrl(config.getDockerRegistryUrl());
            }
            if (!StringHelper.isBlank(config.getDockerRegistryUsername())) {
                clientConfigBuilder.withRegistryUsername(config.getDockerRegistryUsername());
            }
            if (!StringHelper.isBlank(config.getDockerRegistryPassword())) {
                clientConfigBuilder.withRegistryPassword(config.getDockerRegistryPassword());
            }
            DockerClientConfig clientConfig = clientConfigBuilder.build();
            DockerClient client = DockerClientBuilder
                    .getInstance(clientConfig)
                    .withDockerHttpClient(new ApacheDockerHttpClient.Builder()
                            .dockerHost(clientConfig.getDockerHost())
                            .sslConfig(clientConfig.getSSLConfig())
                            .build())
                    .build();
            client.pingCmd().exec();
            return client;
        }
        catch (Exception e) {
            throw new DockerException("Error creating new docker client", e);
        }
    }


    public static DockerClient newClient(String registryUrl) {
        try {
            DockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withRegistryUrl(registryUrl)
                    .build();
            DockerClient client = DockerClientBuilder
                    .getInstance(clientConfig)
                    .withDockerHttpClient(new ApacheDockerHttpClient.Builder()
                            .dockerHost(clientConfig.getDockerHost())
                            .sslConfig(clientConfig.getSSLConfig())
                            .build())
                    .build();
            client.pingCmd().exec();
            return client;
        }
        catch (Exception e) {
            throw new DockerException("Error creating new docker client", e);
        }
    }


    public static void subscribeToLogs(DockerClient client, String containerId) {
        subscribeToLogs(client, containerId, containerId);
    }


    public static void subscribeToLogs(DockerClient client, String containerId, String logPrefix) {
        if (!config.isIncludeDockerLogs()) {
            return;
        }
        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                LOGGER_DOCKER.info("[{}] {}", logPrefix, new String(frame.getPayload()).replaceAll("[\\r\\n]$", ""));
            }
        };
        Thread thread = new Thread(() -> {
            try {
                client.logContainerCmd(containerId)
                        .withStdErr(true)
                        .withStdOut(true)
                        .withFollowStream(true)
                        .withTimestamps(false)
                        .exec(callback);
            }
            catch (Exception e) {
                LOGGER.trace("error receiving logs from docker conainer (containerId: {})", containerId, e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        loggingCallbacks.put(containerId, callback);
    }


    public static void unsubscribeFromLogs(String containerId) {
        ResultCallback.Adapter<Frame> logCallback = loggingCallbacks.remove(containerId);
        if (Objects.nonNull(logCallback)) {
            try {
                logCallback.close();
            }
            catch (Exception e) {
                LOGGER.debug("error unsubscribing from logs (containerId: {})", containerId, e);
            }
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


    private static boolean volumeExists(DockerClient client, String volumeName) {
        return client.listVolumesCmd().exec().getVolumes().stream().anyMatch(x -> Objects.equals(x.getName(), volumeName));
    }


    private static void createVolume(DockerClient client, String volumeName) {
        if (volumeExists(client, volumeName)) {
            LOGGER.debug("volume {} already exists - deleting volume...", volumeName);
            client.removeVolumeCmd(volumeName);
            LOGGER.debug("volume {} deleted", volumeName);
        }
        client.createVolumeCmd()
                .withName(volumeName)
                .withDriver("local")
                .exec();
        LOGGER.debug("volume {} created", volumeName);
    }


    public static void deleteVolume(DockerClient client, String volumeName) {
        if (!volumeExists(client, volumeName)) {
            LOGGER.debug("not able to delete volume - volume {} does not exist", volumeName);
            return;
        }
        List<Container> containers = client.listContainersCmd()
                .withShowAll(true) // Include stopped containers
                .exec();

        boolean isVolumeInUse = false;
        for (Container container: containers) {
            InspectContainerResponse containerDetails = client.inspectContainerCmd(container.getId()).exec();
            Volume[] containerVolumes = containerDetails.getMounts().stream()
                    .map(mount -> new Volume(mount.getDestination().getPath()))
                    .toArray(Volume[]::new);

            for (Volume volume: containerVolumes) {
                if (volume.getPath().equals(volumeName)) {
                    isVolumeInUse = true;
                    LOGGER.debug("Volume is in use by container (volume: {}, containerId: {})", volumeName, container.getId());
                    break;
                }
            }
            if (isVolumeInUse) {
                break;
            }
        }
        if (isVolumeInUse) {
            LOGGER.error("Could not safely delete volume as it is being used (volume: {})", volumeName);
            return;
        }
        client.removeVolumeCmd(volumeName).exec();
        LOGGER.debug("volume {} deleted", volumeName);
    }


    private static void populateVolumeWithData(DockerClient client, ContainerInfo containerInfo) {
        if (StringHelper.isBlank(containerInfo.getMountPathSrc())) {
            return;
        }
        ensureImagePresent(client, TEMP_CONTAINER_IMAGE);
        CreateContainerResponse tempContainer = client.createContainerCmd(TEMP_CONTAINER_IMAGE)
                .withCmd("sh", "-c", "while true; do sleep 1; done") // Keep container running
                .withHostConfig(HostConfig.newHostConfig().withBinds(
                        new Bind(containerInfo.getVolumeName(), new Volume(containerInfo.getMountPathDst()))))
                .exec();
        client.startContainerCmd(tempContainer.getId()).exec();
        try {
            File tempFile = File.createTempFile(containerInfo.getVolumeName(), ".tar");
            CompressArchiveUtil.tar(Paths.get(containerInfo.getMountPathSrc()), tempFile.toPath(), true, true);
            client.copyArchiveToContainerCmd(tempContainer.getId())
                    .withTarInputStream(new FileInputStream(tempFile))
                    .withRemotePath(containerInfo.getMountPathDst())
                    .withDirChildrenOnly(true)
                    .exec();
        }
        catch (Exception e) {
            LOGGER.error("error populating volume with data (volume: {})", containerInfo.getVolumeName(), e);
        }
        finally {
            new Thread(() -> {
                DockerClient newClient = newClient();
                newClient.stopContainerCmd(tempContainer.getId()).exec();
                newClient.removeContainerCmd(tempContainer.getId()).exec();
            }).start();
        }
    }


    public static String createContainer(DockerClient client, ContainerInfo containerInfo) {
        ensureImagePresent(client, containerInfo.getImageName());
        stopAndDeleteContainerByName(client, containerInfo.getContainerName());

        HostConfig hostConfig = new HostConfig()
                .withPortBindings(containerInfo.getPortMappings().entrySet().stream()
                        .map(x -> PortBinding.parse(String.format("%d:%d", x.getKey(), x.getValue())))
                        .toList())
                .withExtraHosts("host.docker.internal:host-gateway")
                .withRestartPolicy(containerInfo.getRestartPolicy())
                .withLinks(containerInfo.getLinkedContainers().entrySet().stream()
                        .map(x -> new Link(x.getKey(), x.getValue()))
                        .toList());
        if (!StringHelper.isBlank(containerInfo.getMountPathSrc())) {
            createVolume(client, containerInfo.getVolumeName());
            try {
                populateVolumeWithData(client, containerInfo);
                hostConfig.withBinds(new Bind(containerInfo.getVolumeName(), new Volume(containerInfo.getMountPathDst())));
            }
            catch (Exception e) {
                deleteVolume(client, containerInfo.getVolumeName());
            }
        }
        if (!StringHelper.isBlank(config.getDockerNetwork())) {
            String actualNetwork = getActualNetwork(client);
            if (!StringHelper.isBlank(actualNetwork)) {
                LOGGER.debug("starting docker container with network (provided: {}, actual: {})", config.getDockerNetwork(), actualNetwork);
                hostConfig.withNetworkMode(actualNetwork);
            }
        }
        return client.createContainerCmd(containerInfo.getImageName())
                .withExposedPorts(containerInfo.getPortMappings().entrySet().stream()
                        .map(x -> new ExposedPort(x.getValue()))
                        .toList())
                .withLabels(Objects.nonNull(containerInfo.getLabels()) ? containerInfo.getLabels() : Map.of())
                .withHostConfig(hostConfig)
                .withName(containerInfo.getContainerName())
                .withEnv(containerInfo.getEnvironmentVariables().entrySet().stream()
                        .map(x -> String.format("%s=%s", x.getKey(), x.getValue()))
                        .toList())
                .exec()
                .getId();
    }


    public static boolean containerExists(DockerClient dockerClient, String containerId) {
        try {
            dockerClient.inspectContainerCmd(containerId).exec();
            return true;
        }
        catch (Exception e) {
            return false;
        }
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
            throw new DockerException(String.format("failed to pull docker image from registry (image: %s, reason: %s)", image, e.getMessage()));
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


    public static void startContainer(DockerClient client, String containerId) {
        if (!containerExists(client, containerId)) {
            throw new IllegalArgumentException(String.format("error starting docker container - container does not exist (containerId: %s)", containerId));
        }
        if (isContainerRunning(client, containerId)) {
            LOGGER.debug("did not start docker container because it is already running (containerId: {})", containerId);
            return;
        }
        client.startContainerCmd(containerId).exec();
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
        return buildImageCmd.exec(new BuildImageResultCallback() {}).awaitImageId();
    }


    public static void publish(DockerClient client, String registry, String imageId, String tag) {
        String actualTag = tag;
        if (!actualTag.startsWith(registry)) {
            actualTag = registry + "/" + tag;
        }
        tag(client, imageId, actualTag);
        client.pushImageCmd(actualTag)
                .exec(new PushImageResultCallback())
                .awaitSuccess();
    }


    public static void tag(DockerClient client, String imageId, String tag) {
        client.tagImageCmd(imageId, tag, DEFAULT_TAG).exec();
    }


    public void pushImage(DockerClient dockerClient, String imageName, String repository) throws InterruptedException {
        dockerClient.pushImageCmd(imageName)
                .withName(repository)
                .exec(new PushImageResultCallback() {}).awaitCompletion();
    }


    public static String getContainerName(Module module) {
        return config.getDtModuleContainerPrefix() + module.getId();
    }


    public static String getContainerName(RestBasedSmartService service) {
        return config.getDtServiceContainerPrefix() + service.getId();
    }


    private static String getActualNetwork(DockerClient client) {
        if (StringHelper.isBlank(config.getDockerContainerName())) {
            return null;
        }
        try {
            String containerSearchName = "^/" + config.getDockerContainerName() + "$";
            LOGGER.trace("searching for containers with name '{}'", containerSearchName);
            List<Container> containers = client.listContainersCmd().withNameFilter(List.of(containerSearchName)).exec();
            if (containers.size() != 1) {
                LOGGER.warn("unable to resolve docker network name (reason: expected to find exactly one container with name '{}' but found {} ({}))",
                        config.getDockerNetwork(),
                        containers.size(),
                        containers.stream()
                                .map(x -> Stream.of(x.getNames()).collect(Collectors.joining(", ", "[", "]")))
                                .collect(Collectors.joining(",")));
                return null;
            }
            if (Objects.isNull(containers.get(0).getNetworkSettings())) {
                return null;
            }
            Map<String, ContainerNetwork> networks = containers.get(0).getNetworkSettings().getNetworks();
            LOGGER.trace("resolving docker network name - found container (name: {}, id: {}, #networks: {})",
                    containers.get(0).getNames(),
                    containers.get(0).getId(),
                    networks.size());
            LOGGER.trace("trying to find exact name match for {}", config.getDockerNetwork());
            if (networks.keySet().contains(config.getDockerNetwork())) {
                LOGGER.debug("resolving docker network name - found network with exact name match (name: {})", config.getDockerNetwork());
                return config.getDockerNetwork();
            }
            LOGGER.trace("no exact name match found for {}", config.getDockerNetwork());
            LOGGER.trace("trying to find exact alias match for {}", config.getDockerNetwork());
            Optional<String> exactMatchAlias = networks.entrySet().stream()
                    .filter(x -> Objects.nonNull(x.getValue().getAliases()))
                    .filter(x -> x.getValue().getAliases().contains(config.getDockerNetwork()))
                    .map(x -> x.getKey())
                    .findFirst();
            if (exactMatchAlias.isPresent()) {
                LOGGER.debug("resolving docker network name - found network with exact alias match (name: {})", exactMatchAlias.get());
                return exactMatchAlias.get();
            }
            LOGGER.trace("no exact alias match found for {}", config.getDockerNetwork());
            LOGGER.trace("trying to find suffix name match for {}", config.getDockerNetwork());
            Optional<String> suffixMatchKey = networks.keySet().stream()
                    .filter(x -> x.endsWith(config.getDockerNetwork()))
                    .findFirst();
            if (suffixMatchKey.isPresent()) {
                LOGGER.debug("resolving docker network name - found network with suffix name match (name: {})", suffixMatchKey.get());
                return suffixMatchKey.get();
            }
            LOGGER.trace("no suffix name match found for {}", config.getDockerNetwork());
            LOGGER.trace("trying to find suffix alias match for {}", config.getDockerNetwork());
            Optional<String> suffixMatchAlias = networks.entrySet().stream()
                    .filter(x -> Objects.nonNull(x.getValue().getAliases()))
                    .filter(x -> x.getValue().getAliases().stream().anyMatch(y -> y.endsWith(config.getDockerNetwork())))
                    .map(x -> x.getKey())
                    .findFirst();
            if (suffixMatchAlias.isPresent()) {
                LOGGER.debug("resolving docker network name - found network with suffix alias match (name: {})", suffixMatchAlias.get());
                return suffixMatchAlias.get();
            }
            LOGGER.trace("no suffix alias match found for {}", config.getDockerNetwork());
            LOGGER.debug("resolving docker network name failed (reason: no matching network found for container name '{}'", config.getDockerContainerName());
        }
        catch (Exception e) {
            LOGGER.warn("resolving docker network name failed", e);
        }
        return null;
    }

    @Getter
    @Setter
    @Builder
    public static class ContainerInfo {
        private String imageName;
        private String containerName;
        private String mountPathSrc;
        private String mountPathDst;
        @Singular
        private Map<String, String> labels;
        @Singular
        private Map<Integer, Integer> portMappings;
        @Singular
        private Map<String, String> environmentVariables;
        @Singular
        private Map<String, String> linkedContainers;
        @Builder.Default
        private RestartPolicy restartPolicy = RestartPolicy.noRestart();

        public String getVolumeName() {
            return "vol-" + containerName;
        }
    }
}
