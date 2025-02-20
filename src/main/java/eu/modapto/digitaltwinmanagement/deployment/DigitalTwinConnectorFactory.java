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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DigitalTwinConnectorFactory {

    private static final DeploymentType DEFAULT_DEPLOYMENT_TYPE = DeploymentType.DOCKER;

    private final DigitalTwinDeploymentDockerConfig dockerConfig;

    @Autowired
    public DigitalTwinConnectorFactory(DigitalTwinDeploymentDockerConfig dockerConfig) {
        this.dockerConfig = dockerConfig;
    }


    public DigitalTwinConnector create(DeploymentType type, DigitalTwinConfig config) throws Exception {
        switch (Optional.ofNullable(type).orElse(DEFAULT_DEPLOYMENT_TYPE)) {
            case DOCKER -> {
                return new DigitalTwinConnectorDocker(config, dockerConfig);
            }
            case INTERNAL -> {
                return new DigitalTwinConnectorInternal(config);
            }
            default -> throw new IllegalArgumentException(String.format("Unsupported DT connector type '%s'", type));
        }
    }
}
