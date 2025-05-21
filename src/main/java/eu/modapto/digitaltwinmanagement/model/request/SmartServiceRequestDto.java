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
package eu.modapto.digitaltwinmanagement.model.request;

import eu.modapto.digitaltwinmanagement.model.ArgumentMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.springframework.validation.annotation.Validated;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Validated
@Schema(name = "SmartServiceRequest")
public class SmartServiceRequestDto {
    public static final String NAME_REGEX = "^[a-zA-Z][a-zA-Z0-9_]*$";

    @Size(min = 1, max = 1023)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String serviceCatalogId;

    @Size(min = 1, max = 128)
    @Schema(pattern = NAME_REGEX)
    @Pattern(regexp = NAME_REGEX)
    private String name;

    @Size(max = 1023)
    private String description;

    private List<SubmodelElement> inputParameters;

    private List<SubmodelElement> outputParameters;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Allows to change input arguments source from user to constant value or to be fetched from another AAS element")
    private Map<String, ArgumentMapping> inputArgumentTypes;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Allows to change output arguments source from user to constant value or to be written to another AAS element")
    private Map<String, ArgumentMapping> outputArgumentTypes;
}
