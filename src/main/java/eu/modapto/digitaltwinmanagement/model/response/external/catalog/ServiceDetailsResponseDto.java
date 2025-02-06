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
package eu.modapto.digitaltwinmanagement.model.response.external.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;


@Data
@NoArgsConstructor
@SuperBuilder
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InternalServiceDetailsResponseDto.class, name = "internal"),
        @JsonSubTypes.Type(value = ExternalServiceDetailsResponseDto.class, name = "external"),
        @JsonSubTypes.Type(value = EmbeddedServiceDetailsResponseDto.class, name = "embedded")
})
public abstract class ServiceDetailsResponseDto {
    private String id;
    private String name;
    @JsonProperty("description_short")
    private String descriptionShort;
    @JsonProperty("description_long")
    private String descriptionLong;
    private String sources;
    private String logo;
    private String affiliation;
    private String contact;
    private String keywords;
    @JsonProperty("input")
    @Builder.Default
    private List<SubmodelElement> inputParameters = new ArrayList<>();
    @JsonProperty("output")
    @Builder.Default
    private List<SubmodelElement> outputParameters = new ArrayList<>();

    public SmartService asSmartService() {
        SmartService result = asSmartServiceInternal();
        result.setServiceCatalogId(id);
        result.setName(name);
        result.setDescription(StringHelper.isBlank(descriptionShort) ? descriptionLong : descriptionShort);
        result.setInputParameters(inputParameters);
        result.setOutputParameters(outputParameters);
        return result;
    }


    protected abstract SmartService asSmartServiceInternal();
}
