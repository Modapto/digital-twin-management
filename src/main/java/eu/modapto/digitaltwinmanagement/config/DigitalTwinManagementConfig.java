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
package eu.modapto.digitaltwinmanagement.config;

import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.ArgumentValidationMode;
import eu.modapto.digitaltwinmanagement.deployment.DeploymentType;
import eu.modapto.digitaltwinmanagement.util.AddressTranslationHelper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "dt-management")
@Getter
@Setter
public class DigitalTwinManagementConfig {
    private String hostname;

    private int port;

    private int externalPort;

    private boolean useProxy;

    private boolean exposeDTsViaContainerName;

    private boolean includeDockerLogs;

    @Value("${dt-management.deployment.liveliness-check.timeout:100000}")
    private int livelinessCheckTimeout;

    @Value("${dt-management.deployment.liveliness-check.interval:500}")
    private int livelinessCheckInterval;

    @Value("${dt-management.deployment.type}")
    private DeploymentType deploymentType;

    @Value("${dt-management.docker.registry.url:}")
    private String dockerRegistryUrl;

    @Value("${dt-management.docker.registry.username:}")
    private String dockerRegistryUsername;

    @Value("${dt-management.docker.registry.password:}")
    private String dockerRegistryPassword;

    @Value("${dt-management.docker.container.name:}")
    private String dockerContainerName;

    @Value("${dt-management.docker.network:}")
    private String dockerNetwork;

    @Value("${dt-management.events.mqtt.host:localhost}")
    private String mqttHost;

    @Value("${dt-management.events.mqtt.host-from-container:}")
    private String mqttHostFromContainer;

    @Value("${dt-management.events.mqtt.port:1883}")
    private int mqttPort;

    @Value("${dt-management.events.mqtt.max-message-size:268435456}")
    private long mqttMaxMessageSize;

    @Value("${dt-management.events.mqtt.queue.size:100}")
    private int mqttQueueSize;

    @Value("${dt-management.events.mqtt.thread.count:1}")
    private int mqttThreadCount;

    @Value("${dt.deployment.docker.image:ghcr.io/modapto/digital-twin:latest}")
    private String dtDockerImage;

    @Value("${dt.loglevel.faaast:INFO}")
    private String dtLoglevelFaaast;

    @Value("${dt.loglevel.external:WARN}")
    private String dtLoglevelExternal;

    @Value("${dt.logging.showStacktrace:true}")
    private boolean dtShowStacktrace;

    @Value("${dt.deployment.docker.restartPolicy:unless-stopped}")
    private String dtRestartPolicy;

    @Value("${dt.deployment.docker.moduleContainerPrefix:modapto-module-}")
    private String dtModuleContainerPrefix;

    @Value("${dt.deployment.docker.serviceContainerPrefix:modapto-service-}")
    private String dtServiceContainerPrefix;

    @Value("${modapto.service-catalogue.host:}")
    private String serviceCatalogueHost;

    @Value("${modapto.service-catalogue.path:}")
    private String serviceCataloguePath;

    @Value("${modapto.dt.operation.input.validation:REQUIRE_PRESENT_OR_DEFAULT}")
    private ArgumentValidationMode dtInputValidationMode;

    @Value("${modapto.dt.operation.output.validation:REQUIRE_PRESENT_OR_DEFAULT}")
    private ArgumentValidationMode dtOutputValidationMode;

    @Value("${modapto.embedded-service.returnResultsForEachStep:true}")
    private boolean embeddedServiceReturnResultsForEachStep;

    public String getHostname() {
        return AddressTranslationHelper.ensureProtocolPresent(hostname);
    }


    @Bean
    public WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> customizer() {
        return x -> x.setPort(port);
    }
}
