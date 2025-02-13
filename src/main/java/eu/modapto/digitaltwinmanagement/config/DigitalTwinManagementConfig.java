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

import eu.modapto.digitaltwinmanagement.deployment.DeploymentType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "dt-management")
@Getter
@Setter
public class DigitalTwinManagementConfig {
    public static final String HOST_DOCKER_INTERNAL = "host.docker.internal";

    private String hostname;

    private int externalPort;

    @Value("${dt-management.deployment.type}")
    private DeploymentType deploymentType;
    @Value("${dt-management.events.mqtt.port}")
    private int mqttPort;
    @Value("${modapto.service-catalogue.url}")
    private String serviceCatalogueUrl;

    @Value("${dt-management.events.mqtt.queue.size:100}")
    private int mqttQueueSize;

    @Value("${dt-management.events.mqtt.thread.count:1}")
    private int mqttThreadCount;

    public String getInternalHostname() {
        return deploymentType == DeploymentType.DOCKER ? HOST_DOCKER_INTERNAL : hostname;
    }
}
