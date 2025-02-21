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
import static eu.modapto.digitaltwinmanagement.model.ArgumentType.CONSTANT;
import static eu.modapto.digitaltwinmanagement.model.ArgumentType.REFERENCE;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.http.HttpAssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.http.provider.config.HttpOperationProviderConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.LambdaExceptionHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinDeploymentDockerConfig;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.exception.DigitalTwinException;
import eu.modapto.digitaltwinmanagement.messagebus.DigitalTwinEventForwarder;
import eu.modapto.digitaltwinmanagement.model.EmbeddedSmartService;
import eu.modapto.digitaltwinmanagement.model.ExternalSmartService;
import eu.modapto.digitaltwinmanagement.model.InternalSmartService;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.RestBasedSmartService;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.util.DockerHelper;
import eu.modapto.digitaltwinmanagement.util.EmbeddedSmartServiceHelper;
import eu.modapto.digitaltwinmanagement.util.EnvironmentHelper;
import eu.modapto.digitaltwinmanagement.util.IdHelper;
import jakarta.annotation.PostConstruct;
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
    public static final String HOST_DOCKER_INTERNAL = "host.docker.internal";
    public static final String LOCALHOST = "localhost";
    private static final String MODAPTO_SUBMODEL_ID_SHORT = "ModaptoSmartServices";
    private static final String MODAPTO_SUBMODEL_SEMANTIC_ID_VALUE = "http://modapto.eu/smt/modapto-smart-services";
    private static final Reference MODAPTO_SUBMODEL_SEMANTIC_ID = ReferenceBuilder.global(MODAPTO_SUBMODEL_SEMANTIC_ID_VALUE);
    private static final long TIMEOUT_REST_AVAILABLE_IN_MS = 60000;
    private static final long INTERVAL_CHECK_REST_AVAILABLE_IN_MS = 500;

    private final Map<Long, DigitalTwinConnector> instances = new HashMap<>();
    private DockerClient dockerClient;
    private boolean dockerAvailable;

    private final DigitalTwinManagementConfig config;

    private final DigitalTwinConnectorFactory connectorFactory;

    private final DigitalTwinEventForwarder eventForwarder;

    private final DigitalTwinDeploymentDockerConfig dockerConfig;

    @Autowired
    public DigitalTwinManager(
            DigitalTwinManagementConfig config,
            DigitalTwinConnectorFactory connectorFactory,
            DigitalTwinEventForwarder eventForwarder,
            DigitalTwinDeploymentDockerConfig dockerConfig) {
        this.config = config;
        this.connectorFactory = connectorFactory;
        this.eventForwarder = eventForwarder;
        this.dockerConfig = dockerConfig;
    }


    @PostConstruct
    private void init() {
        try {
            dockerClient = DockerHelper.newClient(dockerConfig);
            dockerAvailable = true;
        }
        catch (Exception e) {
            dockerAvailable = false;
            LOGGER.warn("Docker connection unsuccessful - Digital Twin Manager will not be able to handle Smart Services of type 'internal'", e);
        }
    }


    public void deploy(Module module) throws Exception {
        if (instances.containsKey(module.getId())) {
            throw new DigitalTwinException(String.format("DT for module already exists (module id: %s)", module.getId()));
        }
        deploy(module, findFreePort());
    }


    private String getModuleToHostAddress(Module module) {
        DeploymentType hostType = config.getDeploymentType();
        DeploymentType moduleType = module.getType();
        if (hostType == DeploymentType.INTERNAL && moduleType == DeploymentType.INTERNAL) {
            return LOCALHOST;
        }
        if (hostType == DeploymentType.INTERNAL && moduleType == DeploymentType.DOCKER) {
            return HOST_DOCKER_INTERNAL;
        }
        if (hostType == DeploymentType.DOCKER && moduleType == DeploymentType.INTERNAL) {
            return LOCALHOST;
        }
        if (hostType == DeploymentType.DOCKER && moduleType == DeploymentType.DOCKER) {
            return System.getenv("HOSTNAME");
        }
        throw new IllegalStateException();
    }


    private Address getHostToModuleAddress(Module module, int port) {
        DeploymentType hostType = config.getDeploymentType();
        DeploymentType moduleType = module.getType();
        if (hostType == DeploymentType.INTERNAL && moduleType == DeploymentType.INTERNAL) {
            return new Address(LOCALHOST, port);
        }
        if (hostType == DeploymentType.INTERNAL && moduleType == DeploymentType.DOCKER) {
            return new Address(LOCALHOST, port);
        }
        if (hostType == DeploymentType.DOCKER && moduleType == DeploymentType.INTERNAL) {
            return new Address(LOCALHOST, port);
        }
        if (hostType == DeploymentType.DOCKER && moduleType == DeploymentType.DOCKER) {
            return new Address(
                    DockerHelper.getContainerName(module),
                    DigitalTwinConnectorDocker.CONTAINER_HTTP_PORT_INTERNAL);
        }
        throw new IllegalStateException();
    }


    private Address getModuleToServiceAddress(SmartService service, int port) {
        DeploymentType moduleType = service.getModule().getType();
        if (service instanceof InternalSmartService internalService && moduleType == DeploymentType.DOCKER) {
            return new Address(
                    DockerHelper.getContainerName(internalService),
                    internalService.getInternalPort());
        }
        return new Address(LOCALHOST, port);
    }


    private void deploy(Module module, int port) throws Exception {
        createActualModel(module);
        DigitalTwinConnector dt = connectorFactory.create(
                module.getType(),
                DigitalTwinConfig.builder()
                        .module(module)
                        .environmentContext(module.getActualModel())
                        .httpPort(port)
                        .messageBusMqttHost(getModuleToHostAddress(module))
                        .messageBusMqttPort(eventForwarder.getMqttPort())
                        .assetConnections(module.getAssetConnections())
                        .build());
        dt.start();
        instances.put(module.getId(), dt);
        eventForwarder.subscribe(module);
        module.setEndpoint("/api/v3.0");
        waitUntilModuleIsRunning(module, port);
        updateEndpoints(module, port);
    }


    private void createActualModel(Module module) throws URISyntaxException, MalformedURLException {
        EnvironmentContext actualModel = EnvironmentHelper.deepCopy(module.getProvidedModel());
        for (var service: module.getServices()) {
            Submodel submodel = getOrCreateModaptoSubmodel(actualModel);
            Operation operation;
            if (service instanceof EmbeddedSmartService embedded) {
                operation = EmbeddedSmartServiceHelper.addSmartService(actualModel, submodel, embedded);
            }
            else if (service instanceof InternalSmartService internal) {
                ensureDockerRunning();
                int port = startContainerForInternalService(internal);
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
            handleInputArgumentTypes(service, operation);
            service.setReference(ReferenceBuilder.forSubmodel(submodel, operation));
            service.setOperationEndpoint(String.format("/submodels/%s/submodel-elements/%s",
                    EncodingHelper.base64UrlEncode(submodel.getId()),
                    service.getName()));
        }
        module.setActualModel(actualModel);
    }


    private void handleInputArgumentTypes(SmartService service, Operation operation) {
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
    }


    private void waitUntilModuleIsRunning(Module module, int port) throws URISyntaxException {
        Address address = getHostToModuleAddress(module, port);
        waitUntilHttpServerIsRunning(
                HttpMethod.GET,
                address.getHost(),
                address.getPort(),
                module.getEndpoint() + "/submodels", "Digital Twin");
        module.getServices().stream()
                .forEach(LambdaExceptionHelper.rethrowConsumer(x -> {
                    waitUntilHttpServerIsRunning(
                            HttpMethod.GET,
                            address.host,
                            address.getPort(),
                            x.getOperationEndpoint(),
                            "Smart Service");
                }));
    }


    private void waitUntilHttpServerIsRunning(InternalSmartService service, int port) throws URISyntaxException {
        waitUntilHttpServerIsRunning(
                HttpMethod.OPTIONS,
                config.getDeploymentType() == DeploymentType.INTERNAL
                        ? LOCALHOST
                        : DockerHelper.getContainerName(service),
                config.getDeploymentType() == DeploymentType.INTERNAL
                        ? port
                        : service.getInternalPort(),
                service.getHttpEndpoint(),
                "Internal Service Docker Container");
    }


    private void waitUntilHttpServerIsRunning(HttpMethod method, String host, int port, String path, String type) throws URISyntaxException {
        waitUntilHttpServerIsRunning(method, String.format("http://%s:%s%s", host, port, path), type);
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
                LOGGER.trace("request failed (reason: {})", e.getCause());
            }
            elapsedTime = Duration.between(startTime, Instant.now()).toMillis();
            try {
                Thread.sleep(INTERVAL_CHECK_REST_AVAILABLE_IN_MS);
            }
            catch (InterruptedException e) {
                LOGGER.trace("Thread interrepted while waiting for {} to become available", type, e);
            }
        }

        throw new DigitalTwinException(String.format(
                "%s could not be started in time (method: %s, endpoint: %s, timeout: %d)", type, method, url, TIMEOUT_REST_AVAILABLE_IN_MS));
    }


    private void updateEndpoints(Module module, int port) {
        if (module.getType() == DeploymentType.DOCKER) {
            module.setEndpoint(String.format("http://%s:%d%s",
                    DockerHelper.getContainerName(module),
                    DigitalTwinConnectorDocker.CONTAINER_HTTP_PORT_INTERNAL,
                    module.getEndpoint()));
        }
        else {
            module.setEndpoint(String.format("http://%s:%d%s", config.getHostname(), port, module.getEndpoint()));
        }
        for (var service: module.getServices()) {
            service.setOperationEndpoint(module.getEndpoint() + service.getOperationEndpoint());
        }
    }


    private Submodel getOrCreateModaptoSubmodel(final EnvironmentContext environmentContext) {
        return environmentContext.getEnvironment().getSubmodels().stream()
                .filter(x -> Objects.equals(MODAPTO_SUBMODEL_ID_SHORT, x.getIdShort()))
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
                .id(String.format("%s/%s", MODAPTO_SUBMODEL_SEMANTIC_ID_VALUE, IdHelper.uuid()))
                .idShort(MODAPTO_SUBMODEL_ID_SHORT)
                .build();
    }


    private void ensureDockerRunning() {
        if (!dockerAvailable) {
            throw new UnsupportedOperationException("Smart Services of type 'internal' not supported as docker connection failed");
        }
    }


    private int startContainerForInternalService(InternalSmartService service) throws URISyntaxException {
        int port = findFreePort();
        String containerId = DockerHelper.startContainer(
                dockerClient,
                DockerHelper.ContainerInfo.builder()
                        .imageName(service.getImage())
                        .containerName(DockerHelper.getContainerName(service))
                        .portMapping(port, service.getInternalPort())
                        .build());
        service.setContainerId(containerId);
        waitUntilHttpServerIsRunning(service, port);
        return port;
    }


    private void stopContainersForInternalServices(Module module) {
        module.getServices().stream()
                .filter(InternalSmartService.class::isInstance)
                .map(InternalSmartService.class::cast)
                .forEach(x -> {
                    try {
                        DockerHelper.removeContainer(dockerClient, x.getContainerId());
                    }
                    catch (DockerException e) {
                        LOGGER.debug("failed to removed docker container for internal smart service (servid ID: {}, container ID: {})", x.getId(), x.getContainerId(),
                                e);
                    }
                    finally {
                        x.setHttpEndpoint(null);
                        x.setContainerId(null);
                    }
                });
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


    private AssetConnectionConfig createAssetConnection(Reference operation, RestBasedSmartService service, int port) throws MalformedURLException {
        String baseUrl;
        String path;
        DeploymentType moduleType = service.getModule().getType();
        if (service instanceof InternalSmartService internalService) {
            if (moduleType == DeploymentType.DOCKER) {
                baseUrl = String.format("http://%s:%d", DockerHelper.getContainerName(internalService), internalService.getInternalPort());
                path = internalService.getHttpEndpoint();
            }
            else {
                baseUrl = String.format("http://%s:%s", LOCALHOST, port);
                path = internalService.getHttpEndpoint();
            }
        }
        else {
            URL url = new URL(service.getHttpEndpoint());
            baseUrl = url.getProtocol() + "://" + url.getHost() + (url.getPort() != -1 ? ":" + url.getPort() : "");
            path = url.getFile();
        }
        LOGGER.debug("Creating asset connection with baseUrl={} and path={}", baseUrl, path);
        return HttpAssetConnectionConfig.builder()
                .baseUrl(baseUrl)
                .operationProvider(operation, HttpOperationProviderConfig.builder()
                        .format("JSON")
                        .method(service.getMethod())
                        .path(path)
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
            throw new DigitalTwinException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        deploy(module, dt.config.getHttpPort());
    }


    public void undeploy(Module module) throws Exception {
        if (!instances.containsKey(module.getId())) {
            throw new DigitalTwinException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        module.setActualModel(null);
        stopContainersForInternalServices(module);
        eventForwarder.unsubscribe(module);
        instances.remove(module.getId());
    }


    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
        catch (IOException e) {
            throw new DigitalTwinException("No free port found", e);
        }
    }

    private static class Address {
        private final String host;
        private final int port;

        private Address(String host, int port) {
            this.host = host;
            this.port = port;
        }


        public String getHost() {
            return host;
        }


        public int getPort() {
            return port;
        }
    }
}
