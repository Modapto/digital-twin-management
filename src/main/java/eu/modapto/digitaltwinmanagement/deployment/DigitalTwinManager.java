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

import static de.fraunhofer.iosb.ilt.faaast.service.request.handler.submodel.AbstractInvokeOperationRequestHandler.SEMANTIC_ID_QUALIFIER_VALUE_BY_REFERENCE;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.http.HttpAssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.http.provider.config.HttpOperationProviderConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.LambdaExceptionHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinDeploymentDockerConfig;
import eu.modapto.digitaltwinmanagement.messagebus.DigitalTwinEventForwarder;
import eu.modapto.digitaltwinmanagement.model.EmbeddedSmartService;
import eu.modapto.digitaltwinmanagement.model.ExternalSmartService;
import eu.modapto.digitaltwinmanagement.model.InternalSmartService;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.RestBasedSmartService;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.request.SmartServiceRequestDto;
import eu.modapto.digitaltwinmanagement.smartservice.embedded.EmbeddedSmartServiceHelper;
import eu.modapto.digitaltwinmanagement.util.Helper;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.Qualifier;
import org.eclipse.digitaltwin.aas4j.v3.model.QualifierKind;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperation;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultQualifier;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;


@Component
public class DigitalTwinManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalTwinManager.class);
    private static final String MODAPTO_SUBMODEL_ID = "ModaptoSmartServices";
    private static final long TIMEOUT_REST_AVAILABLE_IN_MS = 60000;
    private static final long INTERVAL_CHECK_REST_AVAILABLE_IN_MS = 100;
    private static final String HOSTNAME = "localhost";

    private final Map<Long, DigitalTwinConnector> instances = new HashMap<>();
    private DockerClient dockerClient;
    private boolean dockerAvailable;

    @Autowired
    private DigitalTwinConnectorFactory connectorFactory;

    @Autowired
    private DigitalTwinEventForwarder eventForwarder;

    public DigitalTwinManager(DigitalTwinDeploymentDockerConfig dockerConfig) throws URISyntaxException {
        try {
            dockerClient = DockerClientBuilder
                    .getInstance(DefaultDockerClientConfig
                            .createDefaultConfigBuilder()
                            .withRegistryUrl(dockerConfig.getRegistryUrl())
                            .withRegistryUsername(dockerConfig.getRegistryUsername())
                            .withRegistryPassword(dockerConfig.getRegistryPassword())
                            .build())
                    .withDockerHttpClient(new ApacheDockerHttpClient.Builder()
                            .dockerHost(new URI(dockerConfig.getHost()))
                            .build())
                    .build();
            dockerClient.pingCmd().exec();
            dockerAvailable = true;
        }
        catch (Exception e) {
            dockerAvailable = false;
            LOGGER.warn("Docker connection unsuccessful - Digital Twin Manager will not be able to handle Smart Services of type 'internal'", e);
        }
    }


    public void deploy(Module module) throws Exception {
        if (instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module already exists (module id: %s)", module.getId()));
        }
        deploy(module, findFreePort(), findFreePort());
    }


    private void deploy(Module module, int port, int messageBusPort) throws Exception {
        createActualModel(module);
        DigitalTwinConnector dt = connectorFactory.create(
                module.getType(),
                DigitalTwinConfig.builder()
                        .environmentContext(module.getActualModel())
                        .port(port)
                        .messageBusPort(messageBusPort)
                        .assetConnections(module.getAssetConnections())
                        .build());
        dt.start();
        instances.put(module.getId(), dt);
        updateEndpoints(module, port);
        waitUntilModuleIsRunning(module);
        eventForwarder.subscribeToDigitalTwin(module, getMqttEndpoint(messageBusPort));
    }


    private void createActualModel(Module module) throws URISyntaxException {
        EnvironmentContext actualModel = Helper.deepCopy(module.getProvidedModel());
        for (var service: module.getServices()) {
            validateServiceName(service);
            Submodel submodel = getOrCreateModaptoSubmodel(actualModel);
            Operation operation;
            if (service instanceof EmbeddedSmartService embedded) {
                operation = EmbeddedSmartServiceHelper.addSmartService(actualModel, submodel, embedded);
            }
            else if (service instanceof InternalSmartService internal) {
                ensureDockerRunning();
                int port = startContainerForInternalService(internal);
                internal.setHttpEndpoint(internal.getHttpEndpoint().replace("${container}", "localhost:" + port));
                operation = addOperationNormal(service);
                submodel.getSubmodelElements().add(operation);
                module.getAssetConnections().add(createAssetConnection(
                        ReferenceBuilder.forSubmodel(submodel, operation),
                        internal,
                        port));
            }
            else if (service instanceof ExternalSmartService external) {
                operation = addOperationNormal(service);
                submodel.getSubmodelElements().add(operation);
                module.getAssetConnections().add(createAssetConnection(
                        ReferenceBuilder.forSubmodel(submodel, operation),
                        external));
            }
            else {
                throw new IllegalArgumentException(String.format("Unsupported smart service type (type: %s)", service.getClass().getSimpleName()));
            }
            for (var entry: service.getInputArgumentTypes().entrySet()) {
                switch (entry.getValue().getType()) {
                    case CONSTANT: {
                        Optional<OperationVariable> variable = operation.getInputVariables().stream()
                                .filter(x -> Objects.equals(x.getValue().getIdShort(), entry.getKey()))
                                .findFirst();
                        if (variable.isPresent()) {
                            ((Property) variable.get().getValue()).setValue(entry.getValue().getValue());
                        }
                        break;
                    }
                    case REFERENCE: {
                        Optional<OperationVariable> variable = operation.getInputVariables().stream()
                                .filter(x -> Objects.equals(x.getValue().getIdShort(), entry.getKey()))
                                .findFirst();
                        if (variable.isPresent()) {
                            Qualifier qualifier = new DefaultQualifier.Builder()
                                    .kind(QualifierKind.VALUE_QUALIFIER)
                                    .semanticId(SEMANTIC_ID_QUALIFIER_VALUE_BY_REFERENCE)
                                    .valueId(ReferenceHelper.parse(entry.getValue().getValue()))
                                    .build();
                            variable.get().getValue().getQualifiers().add(qualifier);
                        }
                        break;
                    }
                    default:
                        operation.getInputVariables().stream()
                                .filter(x -> Objects.equals(x.getValue().getIdShort(), entry.getKey()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException(String.format(
                                        "invalid input argument type info - argument does not exist (argument name: %s)",
                                        entry.getKey())));
                        break;
                }
            }
            service.setReference(ReferenceBuilder.forSubmodel(submodel, operation));
            service.setOperationEndpoint(String.format("/submodels/%s/submodel-elements/%s",
                    EncodingHelper.base64UrlEncode(submodel.getId()),
                    service.getName()));
        }
        module.setActualModel(actualModel);
    }


    private void waitUntilModuleIsRunning(Module module) throws URISyntaxException {
        waitUntilHttpServerIsRunning(HttpMethod.GET, module.getEndpoint() + "/submodels", "Digital Twin");
        module.getServices().stream()
                .filter(InternalSmartService.class::isInstance)
                .map(InternalSmartService.class::cast)
                .forEach(LambdaExceptionHelper.rethrowConsumer(x -> {
                    waitUntilHttpServerIsRunning(HttpMethod.OPTIONS, x.getHttpEndpoint(), "Internal Service Docker Container");
                    waitUntilHttpServerIsRunning(HttpMethod.GET, x.getOperationEndpoint(), "Smart Service");
                }));
    }


    private void waitUntilHttpServerIsRunning(HttpMethod method, String url, String type) throws URISyntaxException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .method(method.toString(), HttpRequest.BodyPublishers.noBody())
                .uri(new URI(url))
                .timeout(Duration.ofSeconds(10))
                .build();
        Instant startTime = Instant.now();
        long elapsedTime = 0;
        LOGGER.debug("Waiting for {} to become available... (method: {}, endpoint: {}, timeout: {})", type, method, url, TIMEOUT_REST_AVAILABLE_IN_MS);
        while (elapsedTime < TIMEOUT_REST_AVAILABLE_IN_MS) {
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 || response.statusCode() < 300) {
                    LOGGER.debug("{} available... (method: {}, endpoint: {})", type, method, url);
                    return;
                }
                LOGGER.debug("request failed (status code: {})", response.statusCode());
            }
            catch (IOException | InterruptedException e) {
                LOGGER.debug("request failed (reason: {})", e.getCause(), e);
            }
            elapsedTime = Duration.between(startTime, Instant.now()).toMillis();
            try {
                Thread.sleep(INTERVAL_CHECK_REST_AVAILABLE_IN_MS);
            }
            catch (InterruptedException e) {
                LOGGER.debug("Thread interrepted while waiting for {} to become available", type, e);
            }
        }

        throw new RuntimeException(String.format(
                "{} could not be started in time (method: {}, endpoint: %s, timeout: %d)", type, method, url, TIMEOUT_REST_AVAILABLE_IN_MS));
    }


    private String getMqttEndpoint(int port) {
        return String.format("tcp://%s:%d", HOSTNAME, port);
    }


    private void updateEndpoints(Module module, int port) {
        module.setEndpoint(String.format("http://%s:%d/api/v3.0", HOSTNAME, port));
        for (var service: module.getServices()) {
            service.setOperationEndpoint(module.getEndpoint() + service.getOperationEndpoint());
        }
    }


    private void validateServiceName(SmartService service) {
        if (Objects.isNull(service)
                || Objects.isNull(service.getName())
                || service.getName().length() > 128
                || !service.getName().matches(SmartServiceRequestDto.NAME_REGEX)) {
            throw new IllegalArgumentException(String.format("invalid service name - must match regex '%s' and be max. 128 characters long", SmartServiceRequestDto.NAME_REGEX));
        }
    }


    private Submodel getOrCreateModaptoSubmodel(final EnvironmentContext environmentContext) {
        return environmentContext.getEnvironment().getSubmodels().stream()
                .filter(x -> Objects.equals(MODAPTO_SUBMODEL_ID, x.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Submodel submodel = createModaptoSubmodel();
                    environmentContext.getEnvironment().getSubmodels().add(submodel);
                    environmentContext.getEnvironment().getAssetAdministrationShells().get(0).getSubmodels().add(ReferenceBuilder.forSubmodel(submodel));
                    return submodel;
                });
    }


    private Submodel createModaptoSubmodel() {
        return new DefaultSubmodel.Builder()
                .id(MODAPTO_SUBMODEL_ID)
                .build();
    }

    private void ensureDockerRunning() {
        if (!dockerAvailable) {
            throw new UnsupportedOperationException("Smart Services of type 'internal' not supported as docker connection failed");
        }
    }

    private int startContainerForInternalService(InternalSmartService service) {
        int port = findFreePort();
        CreateContainerResponse container = dockerClient.createContainerCmd(service.getImage())
                .withExposedPorts(new ExposedPort(service.getInternalPort()))
                .withHostConfig(new HostConfig()
                        .withPortBindings(
                                PortBinding.parse(String.format("%d:%d", port, service.getInternalPort()))))
                .exec();
        StartContainerCmd startCmd = dockerClient.startContainerCmd(container.getId());
        startCmd.exec();
        service.setContainerId(container.getId());
        return port;
    }


    private AssetConnectionConfig createAssetConnection(Reference operation, RestBasedSmartService service) {
        try {
            URL url = new URL(service.getHttpEndpoint());
            return createAssetConnection(operation, service, url.getPort());
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format(
                    "creating asset connection failed because of malformed service URL (reason: %s)", e.getMessage()));
        }
    }


    private AssetConnectionConfig createAssetConnection(Reference operation, RestBasedSmartService service, int port) {
        URL url;
        URL baseUrl;
        String urlPath;
        try {
            url = new URL(service.getHttpEndpoint());
            baseUrl = UriBuilder.newInstance()
                    .host(url.getHost())
                    .port(port > 0 ? port : url.getPort())
                    .scheme(url.getProtocol())
                    .userInfo(url.getUserInfo())
                    .build()
                    .toURL();
            urlPath = url.getPath();
            if (!StringHelper.isBlank(url.getQuery())) {
                urlPath += "?" + url.getQuery();
            }
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format(
                    "creating asset connection failed because of malformed service URL (reason: %s)", e.getMessage()));
        }
        return HttpAssetConnectionConfig.builder()
                .baseUrl(baseUrl)
                .operationProvider(operation, HttpOperationProviderConfig.builder()
                        .format("JSON")
                        .method(service.getMethod())
                        .path(urlPath)
                        .headers(service.getHeaders())
                        .template(service.getPayload())
                        .queries(service.getOutputMapping())
                        .build())
                .build();
    }


    private Operation addOperationNormal(SmartService service) {
        return new DefaultOperation.Builder()
                .idShort(service.getName())
                .inputVariables(service.getInputParameters().stream()
                        .map(x -> new DefaultOperationVariable.Builder()
                                .value(x)
                                .build())
                        .collect(Collectors.toList()))
                .outputVariables(service.getOutputParameters().stream()
                        .map(x -> new DefaultOperationVariable.Builder()
                                .value(x)
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }


    public void update(Module module) throws Exception {
        if (!instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        deploy(module, dt.config.getPort(), dt.config.getMessageBusPort());
    }


    public void undeploy(Module module) throws Exception {
        if (!instances.containsKey(module.getId())) {
            throw new RuntimeException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        eventForwarder.unsubscribeFromDigitalTwin(module);
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        module.setActualModel(null);
        instances.remove(module.getId());
    }


    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
        catch (IOException e) {
            throw new RuntimeException("No free port found", e);
        }
    }
}
