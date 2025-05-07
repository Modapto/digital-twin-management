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

import de.fraunhofer.iosb.ilt.faaast.service.dataformat.DeserializationException;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.EnvironmentSerializationManager;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.SerializationException;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.request.ModuleRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.ModuleDetailsResponseDto;
import eu.modapto.digitaltwinmanagement.model.response.ModuleResponseDto;
import java.io.ByteArrayInputStream;
import java.util.Base64;


public class ModuleMapper {

    private ModuleMapper() {}


    public static Module toEntity(ModuleRequestDto requestDto) throws DeserializationException {
        return Module.builder()
                .name(requestDto.getName())
                .providedModel(EnvironmentSerializationManager
                        .deserializerFor(requestDto.getFormat())
                        .read(new ByteArrayInputStream(Base64.getDecoder().decode(requestDto.getAas()))))
                .type(requestDto.getType())
                .assetConnections(requestDto.getAssetConnections())
                .build();
    }


    public static ModuleResponseDto toDto(Module module) {
        return ModuleResponseDto.builder()
                .id(module.getId())
                .name(module.getName())
                .endpoint(module.getExternalEndpoint())
                .services(module.getServices().stream().map(SmartServiceMapper::toDto).toList())
                .build();
    }


    public static ModuleDetailsResponseDto toDetailsDto(Module module) throws SerializationException {
        return ModuleDetailsResponseDto.builder()
                .actualModel(new String(EncodingHelper.base64Encode(EnvironmentSerializationManager
                        .serializerFor(DataFormat.JSON)
                        .write(module.getActualModel()))))
                .providedModel(new String(EncodingHelper.base64Encode(EnvironmentSerializationManager
                        .serializerFor(DataFormat.JSON)
                        .write(module.getProvidedModel()))))
                .build();
    }
}
