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
package eu.modapto.digitaltwinmanagement.mapper;

import eu.modapto.digitaltwinmanagement.model.ArgumentMapping;
import eu.modapto.digitaltwinmanagement.model.ArgumentType;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.response.SmartServiceResponseDto;


public class SmartServiceMapper {

    private SmartServiceMapper() {}


    public static SmartServiceResponseDto toDto(SmartService service) {
        return SmartServiceResponseDto.builder()
                .id(service.getId())
                .serviceCatalogId(service.getServiceCatalogId())
                .endpoint(service.getExternalEndpoint())
                .name(service.getName())
                .description(service.getDescription())
                .inputArgumentTypes(service.getInputArgumentTypes())
                .outputArgumentTypes(service.getOutputArgumentTypes())
                .actualInputParameters(service.getInputParameters().stream()
                        .filter(x -> service.getInputArgumentTypes()
                                .getOrDefault(x.getIdShort(), ArgumentMapping.builder().type(ArgumentType.USER).build())
                                .getType() == ArgumentType.USER)
                        .toList())
                .actualOutputParameters(service.getOutputParameters().stream()
                        .filter(x -> service.getOutputArgumentTypes()
                                .getOrDefault(x.getIdShort(), ArgumentMapping.builder().type(ArgumentType.USER).build())
                                .getType() == ArgumentType.USER)
                        .toList())
                .build();
    }
}
