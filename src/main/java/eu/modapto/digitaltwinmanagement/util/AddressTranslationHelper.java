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

import de.fraunhofer.iosb.ilt.faaast.service.model.SubmodelElementIdentifier;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.deployment.DeploymentType;
import eu.modapto.digitaltwinmanagement.deployment.DigitalTwinConnectorDocker;
import eu.modapto.digitaltwinmanagement.model.Address;
import eu.modapto.digitaltwinmanagement.model.InternalSmartService;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.RestBasedSmartService;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AddressTranslationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddressTranslationHelper.class);
    public static final String LOCALHOST = "localhost";
    public static final String HOST_DOCKER_INTERNAL = "host.docker.internal";
    private static final String PROTOCOL_SEPARATOR = "://";
    private static final String PREFIX_HTTP = "http" + PROTOCOL_SEPARATOR;
    public static final String MODULE_DEFAULT_PATH = "/api/v3.0";
    public static final String SERVICE_DEFAULT_PATH = "/submodels/%s/submodel-elements/%s";
    private static DigitalTwinManagementConfig config;

    public static void setConfig(DigitalTwinManagementConfig dtConfig) {
        config = dtConfig;
    }


    public static String getInternalEndpoint(Module module) {
        return getHostToModuleAddress(module, module.getExternalPort()).asUrl() + MODULE_DEFAULT_PATH;
    }


    public static String getExternalEndpoint(Module module) {
        String baseUrl;
        if (config.isUseProxy()) {
            baseUrl = config.getHostname();
            if (config.getExternalPort() > 0) {
                baseUrl += ":" + config.getExternalPort();
            }
            baseUrl += "/digital-twins/" + module.getId();
        }
        else {
            baseUrl = ensureProtocolPresent(
                    config.isExposeDTsViaContainerName()
                            ? String.format("%s:%d", DockerHelper.getContainerName(module), DigitalTwinConnectorDocker.CONTAINER_HTTP_PORT_INTERNAL)
                            : String.format("%s:%d", config.getHostname(), module.getExternalPort()));
        }
        return baseUrl + MODULE_DEFAULT_PATH;
    }


    public static String getInternalEndpoint(SmartService service) {
        return getInternalEndpoint(service.getModule()) + getServiceUrlPath(service);
    }


    public static String getExternalEndpoint(SmartService service) {
        return getExternalEndpoint(service.getModule()) + getServiceUrlPath(service);
    }


    public static Address getHostToModuleAddress(Module module, int port) {
        DeploymentType hostType = config.getDeploymentType();
        DeploymentType moduleType = module.getType();
        if (hostType == DeploymentType.INTERNAL && moduleType == DeploymentType.INTERNAL
                || hostType == DeploymentType.INTERNAL && moduleType == DeploymentType.DOCKER
                || hostType == DeploymentType.DOCKER && moduleType == DeploymentType.INTERNAL) {
            return Address.builder()
                    .host(LOCALHOST)
                    .port(port)
                    .build();

        }
        if (hostType == DeploymentType.DOCKER && moduleType == DeploymentType.DOCKER) {
            return Address.builder()
                    .host(DockerHelper.getContainerName(module))
                    .port(DigitalTwinConnectorDocker.CONTAINER_HTTP_PORT_INTERNAL)
                    .build();
        }
        throw new IllegalStateException();
    }


    public static Address getHostToInternalService(InternalSmartService service, int port) {
        DeploymentType hostType = config.getDeploymentType();
        if (hostType == DeploymentType.DOCKER) {
            return Address.builder()
                    .host(DockerHelper.getContainerName(service))
                    .path(service.getHttpEndpoint())
                    .port(service.getInternalPort())
                    .build();
        }
        return Address.builder()
                .host(LOCALHOST)
                .path(service.getHttpEndpoint())
                .port(port)
                .build();
    }


    public static String getModuleToHostAddress(Module module) {
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
            return StringHelper.isBlank(config.getMqttHostFromContainer())
                    ? config.getDockerContainerName()
                    : config.getMqttHostFromContainer();
        }
        LOGGER.error("unable to resolve module to host address (moduleId: {}, hostType: {}, moduleType: {}, dockerContainerName: {}, mqttHostFromContainer: {})",
                module.getId(),
                hostType,
                moduleType,
                config.getDockerContainerName(),
                config.getMqttHostFromContainer());
        throw new IllegalStateException();
    }


    public static Address getModuleToServiceAddress(RestBasedSmartService service, int port) {
        DeploymentType moduleType = service.getModule().getType();
        if (service instanceof InternalSmartService internalService) {
            if (moduleType == DeploymentType.DOCKER) {
                return Address.builder()
                        .host(DockerHelper.getContainerName(internalService))
                        .port(internalService.getInternalPort())
                        .path(internalService.getHttpEndpoint())
                        .build();
            }
            else {
                return Address.builder()
                        .host(LOCALHOST)
                        .port(port)
                        .path(internalService.getHttpEndpoint())
                        .build();
            }
        }
        else {
            return new Address(service.getHttpEndpoint());
        }
    }


    public static String ensureProtocolPresent(String url) {
        if (url.contains(PROTOCOL_SEPARATOR)) {
            return url;
        }
        return PREFIX_HTTP + url;
    }


    private static String getServiceUrlPath(SmartService service) {
        SubmodelElementIdentifier identifier = SubmodelElementIdentifier.fromReference(service.getReference());
        return String.format(SERVICE_DEFAULT_PATH,
                EncodingHelper.base64UrlEncode(identifier.getSubmodelId()),
                identifier.getIdShortPath());
    }


    private AddressTranslationHelper() {}
}
