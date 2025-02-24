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

import eu.modapto.digitaltwinmanagement.config.DigitalTwinDeploymentDockerConfig;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.util.AddressTranslationHelper;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DigitalTwinConnectorFactory {

    private static final DeploymentType DEFAULT_DEPLOYMENT_TYPE = DeploymentType.DOCKER;

    private final DigitalTwinManagementConfig config;
    private final DigitalTwinDeploymentDockerConfig dockerConfig;

    @Autowired
    public DigitalTwinConnectorFactory(DigitalTwinManagementConfig config, DigitalTwinDeploymentDockerConfig dockerConfig) {
        this.config = config;
        this.dockerConfig = dockerConfig;
    }


    public DigitalTwinConnector create(Module module) throws Exception {
        DigitalTwinConfig dtConfig = DigitalTwinConfig.builder()
                .module(module)
                .environmentContext(module.getActualModel())
                .httpPort(module.getExternalPort())
                .messageBusMqttHost(AddressTranslationHelper.getModuleToHostAddress(module))
                .messageBusMqttPort(config.getMqttPort())
                .assetConnections(module.getAssetConnections())
                .build();
        switch (Optional.ofNullable(module.getType()).orElse(DEFAULT_DEPLOYMENT_TYPE)) {
            case DOCKER -> {
                return new DigitalTwinConnectorDocker(dtConfig, dockerConfig);
            }
            case INTERNAL -> {
                return new DigitalTwinConnectorInternal(dtConfig);
            }
            default -> throw new IllegalArgumentException(String.format("Unsupported DT connector type '%s'", module.getType()));
        }
    }
}
