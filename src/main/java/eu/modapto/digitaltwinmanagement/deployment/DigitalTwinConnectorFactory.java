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

import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.exception.BadRequestException;
import eu.modapto.digitaltwinmanagement.model.EmbeddedSmartService;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.util.AddressTranslationHelper;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DigitalTwinConnectorFactory {

    private static final DeploymentType DEFAULT_DEPLOYMENT_TYPE = DeploymentType.DOCKER;

    private final DigitalTwinManagementConfig config;

    @Autowired
    public DigitalTwinConnectorFactory(DigitalTwinManagementConfig config) {
        this.config = config;
    }


    public DigitalTwinConnector create(Module module) throws Exception {
        boolean smtSimulationReturnResultsForEachStep = true;
        List<Boolean> temp = module.getServices().stream()
                .filter(EmbeddedSmartService.class::isInstance)
                .map(EmbeddedSmartService.class::cast)
                .map(x -> x.isReturnResultsForEachStep())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .toList();
        if (temp.isEmpty()) {
            smtSimulationReturnResultsForEachStep = config.isEmbeddedServiceReturnResultsForEachStep();
        }
        else if (temp.size() == 1) {
            smtSimulationReturnResultsForEachStep = temp.get(0);
        }
        else {
            throw new BadRequestException("conflicting configuration - ReturnResultsForEachStep both true and false for some embedded services");
        }

        DigitalTwinConfig dtConfig = DigitalTwinConfig.builder()
                .module(module)
                .environmentContext(module.getActualModel())
                .httpPort(module.getExternalPort())
                .messageBusMqttHost(AddressTranslationHelper.getModuleToHostAddress(module))
                .messageBusMqttPort(config.getMqttPort())
                .assetConnections(module.getAssetConnections())
                .smtSimulationReturnResultsForEachStep(smtSimulationReturnResultsForEachStep)
                .build();
        switch (Optional.ofNullable(module.getType()).orElse(DEFAULT_DEPLOYMENT_TYPE)) {
            case DOCKER -> {
                return new DigitalTwinConnectorDocker(config, dtConfig);
            }
            case INTERNAL -> {
                return new DigitalTwinConnectorInternal(config, dtConfig);
            }
            default -> throw new IllegalArgumentException(String.format("Unsupported DT connector type '%s'", module.getType()));
        }
    }
}
