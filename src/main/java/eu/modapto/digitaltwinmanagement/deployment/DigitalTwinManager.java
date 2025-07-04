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
import com.github.dockerjava.api.model.RestartPolicy;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.http.HttpAssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.http.provider.config.HttpOperationProviderConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.util.LambdaExceptionHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.PortHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.exception.DigitalTwinException;
import eu.modapto.digitaltwinmanagement.messagebus.DigitalTwinEventForwarder;
import eu.modapto.digitaltwinmanagement.model.Address;
import eu.modapto.digitaltwinmanagement.model.EmbeddedSmartService;
import eu.modapto.digitaltwinmanagement.model.ExternalSmartService;
import eu.modapto.digitaltwinmanagement.model.InternalSmartService;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.RestBasedSmartService;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.repository.LiveModuleRepository;
import eu.modapto.digitaltwinmanagement.util.AddressTranslationHelper;
import eu.modapto.digitaltwinmanagement.util.DockerHelper;
import eu.modapto.digitaltwinmanagement.util.EmbeddedSmartServiceHelper;
import eu.modapto.digitaltwinmanagement.util.EnvironmentHelper;
import eu.modapto.digitaltwinmanagement.util.IdHelper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
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
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;


@Component
@DependsOn("app-id")
public class DigitalTwinManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalTwinManager.class);
    private static final String MODAPTO_SUBMODEL_ID_SHORT = "ModaptoSmartServices";
    private static final String MODAPTO_SUBMODEL_SEMANTIC_ID_VALUE = "http://modapto.eu/smt/modapto-smart-services";
    private static final Reference MODAPTO_SUBMODEL_SEMANTIC_ID = ReferenceBuilder.global(MODAPTO_SUBMODEL_SEMANTIC_ID_VALUE);

    private final DigitalTwinManagementConfig config;

    private final LiveModuleRepository liveModuleRepository;
    private final DigitalTwinConnectorFactory connectorFactory;

    private final Map<String, DigitalTwinConnector> instances = new HashMap<>();
    private DockerClient dockerClient;
    private boolean dockerAvailable;

    @Autowired
    public DigitalTwinManager(
            DigitalTwinManagementConfig config,
            LiveModuleRepository liveModuleRepository,
            DigitalTwinConnectorFactory connectorFactory,
            DigitalTwinEventForwarder eventForwarder) {
        this.config = config;
        this.liveModuleRepository = liveModuleRepository;
        this.connectorFactory = connectorFactory;
    }


    @PostConstruct
    private void init() {
        try {
            dockerClient = DockerHelper.newClient();
            dockerAvailable = true;
        }
        catch (Exception e) {
            dockerAvailable = false;
            LOGGER.warn("Docker connection unsuccessful - Digital Twin Manager will not be able to handle Smart Services of type 'internal'", e);
        }
        liveModuleRepository.getAll().forEach(x -> {
            try {
                DigitalTwinConnector connector = connectorFactory.create(x);
                connector.recreate();
                liveModuleRepository.update(x);
                instances.put(x.getId(), connector);
            }
            catch (Exception e) {
                LOGGER.warn("failed to re-create Digital Twin connector (moduleId: {}, reason: {})", x.getId(), e.getMessage(), e);
            }
        });
    }


    public void deploy(Module module) throws Exception {
        if (instances.containsKey(module.getId())) {
            throw new DigitalTwinException(String.format("DT for module already exists (module id: %s)", module.getId()));
        }
        deploy(module, PortHelper.findFreePort());
    }


    private void deploy(Module module, int port) throws Exception {
        LOGGER.debug("deploying module... (moduleId: {}, port: {})", module.getId(), port);
        module.setExternalPort(port);
        createActualModel(module);
        DigitalTwinConnector dt = connectorFactory.create(module);
        dt.start();
        instances.put(module.getId(), dt);
        liveModuleRepository.subscribe(module);
        waitUntilModuleIsRunning(module);
        LOGGER.debug("module deployed (moduleId: {})", module.getId());
    }


    private void createActualModel(Module module) throws URISyntaxException, MalformedURLException {
        LOGGER.debug("creating actual model via copy...");
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


    private void waitUntilModuleIsRunning(Module module) throws URISyntaxException {
        LOGGER.debug("waiting for module to become available... (moduleId: {})", module.getId());
        waitUntilHttpServerIsRunning(
                HttpMethod.GET,
                module.getInternalEndpoint() + "/submodels",
                "Digital Twin");
        module.getServices().stream()
                .forEach(LambdaExceptionHelper.rethrowConsumer(x -> {
                    waitUntilHttpServerIsRunning(
                            HttpMethod.GET,
                            x.getInternalEndpoint(),
                            "Smart Service");
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
        LOGGER.debug("Waiting for {} to become available... (method: {}, endpoint: {}, timeout: {})", type, method, url, config.getLivelinessCheckTimeout());
        while (elapsedTime < config.getLivelinessCheckTimeout()) {
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
                Thread.sleep(config.getLivelinessCheckInterval());
            }
            catch (InterruptedException e) {
                LOGGER.trace("Thread interrepted while waiting for {} to become available", type, e);
            }
        }

        throw new DigitalTwinException(String.format("%s could not be started in time (method: %s, endpoint: %s, timeout: %d)",
                type,
                method,
                url,
                config.getLivelinessCheckTimeout()));
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
        int port = PortHelper.findFreePort();
        LOGGER.debug("starting docker container for internal smart service (serviceId: {}, image: {}, port: {})", service.getId(), service.getImage(), port);
        String containerId = DockerHelper.startContainer(
                dockerClient,
                DockerHelper.ContainerInfo.builder()
                        .imageName(service.getImage())
                        .containerName(DockerHelper.getContainerName(service))
                        .portMapping(port, service.getInternalPort())
                        .restartPolicy(RestartPolicy.parse(config.getDtRestartPolicy()))
                        .label("modapto-type", "service")
                        .build());
        DockerHelper.subscribeToLogs(dockerClient, containerId, "service-" + service.getId());
        service.setContainerId(containerId);
        LOGGER.info("docker container for internal smart service started (serviceId: {}, containerId: {})", service.getId(), containerId);
        waitUntilHttpServerIsRunning(
                HttpMethod.OPTIONS,
                AddressTranslationHelper.getHostToInternalService(service, port).asUrl(),
                "Internal Service Docker Container");
        return port;
    }


    private void stopContainersForInternalServices(Module module) {
        module.getServices().stream()
                .filter(InternalSmartService.class::isInstance)
                .map(InternalSmartService.class::cast)
                .forEach(x -> {
                    try {
                        DockerHelper.unsubscribeFromLogs(x.getContainerId());
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
        Address address = AddressTranslationHelper.getModuleToServiceAddress(service, port);
        LOGGER.debug("Creating asset connection (address: {})", address);
        return HttpAssetConnectionConfig.builder()
                .baseUrl(address.getBaseUrl())
                .operationProvider(operation, HttpOperationProviderConfig.builder()
                        .format("JSON")
                        .method(service.getMethod())
                        .path(address.getPath())
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
        LOGGER.debug("updating module... (moduleId: {}, containerId: {})", module.getId(), module.getContainerId());
        if (!instances.containsKey(module.getId())) {
            throw new DigitalTwinException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        deploy(module, dt.dtConfig.getHttpPort());
    }


    public void undeploy(Module module) throws Exception {
        if (!instances.containsKey(module.getId())) {
            throw new DigitalTwinException(String.format("DT for module does not exist (module id: %s)", module.getId()));
        }
        DigitalTwinConnector dt = instances.get(module.getId());
        dt.stop();
        module.setActualModel(null);
        stopContainersForInternalServices(module);
        liveModuleRepository.unsubscribe(module);
        instances.remove(module.getId());
    }
}
