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

    private boolean useProxy;

    @Value("${dt-management.deployment.type}")
    private DeploymentType deploymentType;

    @Value("${modapto.service-catalogue.host}")
    private String serviceCatalogueHost;

    @Value("${modapto.service-catalogue.path}")
    private String serviceCataloguePath;

    @Value("${dt-management.events.mqtt.host:localhost}")
    private String mqttHost;

    @Value("${dt-management.events.mqtt.port}")
    private int mqttPort;

    @Value("${dt-management.events.mqtt.queue.size:100}")
    private int mqttQueueSize;

    @Value("${dt-management.events.mqtt.thread.count:1}")
    private int mqttThreadCount;

    @Bean
    public WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> customizer() {
        return x -> x.setPort(port);
    }
}
