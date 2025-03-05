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

import de.fraunhofer.iosb.ilt.faaast.service.endpoint.http.HttpEndpointConfig;
import de.fraunhofer.iosb.ilt.faaast.service.messagebus.mqtt.MessageBusMqttConfig;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.util.IdHelper;
import eu.modapto.dt.faaast.service.smt.simulation.SimulationSubmodelTemplateProcessorConfig;


public abstract class DigitalTwinConnector {

    protected DigitalTwinManagementConfig config;
    protected DigitalTwinConfig dtConfig;

    protected DigitalTwinConnector(DigitalTwinManagementConfig config, DigitalTwinConfig dtConfig) {
        this.config = config;
        this.dtConfig = dtConfig;
    }


    protected HttpEndpointConfig getHttpEndpointConfig(int port) {
        return HttpEndpointConfig.builder()
                .cors(true)
                .port(port)
                .sni(false)
                .ssl(false)
                .includeErrorDetails(true)
                .build();
    }


    protected MessageBusMqttConfig getMessageBusMqttConfig() {
        return MessageBusMqttConfig.builder()
                .host(dtConfig.getMessageBusMqttHost())
                .internal(false)
                .port(dtConfig.getMessageBusMqttPort())
                .topicPrefix(String.format("module/%s/", dtConfig.getModule().getId()))
                .clientId(String.format("module-%s-%s", dtConfig.getModule().getId(), IdHelper.uuidAlphanumeric8()))
                .build();
    }


    protected SimulationSubmodelTemplateProcessorConfig getSimulationSubmodelTemplateProcessorConfig() {
        return SimulationSubmodelTemplateProcessorConfig.builder().build();
    }


    public abstract void start();


    public abstract void stop();


    public abstract void recreate();
}
