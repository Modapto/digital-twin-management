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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Getter
@Setter
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "dt-management.test")
public class TestConfig {
    private int localDockerRegistryInternalPort = 5000;
    private int localDockerRegistryExternalPort = 5000;
    private String localDockerRegistryImage = "registry:2";

    @Value("${dt.test.deployment.type:DOCKER}")
    private DeploymentType dtDeplyomentType;
}
