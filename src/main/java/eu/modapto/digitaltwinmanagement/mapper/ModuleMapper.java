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

import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.request.ModuleRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.ModuleResponseDto;


public class ModuleMapper {

    private ModuleMapper() {}


    public static Module toEntity(ModuleRequestDto requestDto) {
        Module module = new Module();
        module.setAas(requestDto.getAas());
        module.setType(requestDto.getType());
        return module;
    }


    public static ModuleResponseDto toDto(Module module) {
        ModuleResponseDto responseDto = new ModuleResponseDto();
        responseDto.setId(module.getId());
        responseDto.setUri(module.getUri());
        responseDto.setServices(module.getServices());
        return responseDto;
    }
}
