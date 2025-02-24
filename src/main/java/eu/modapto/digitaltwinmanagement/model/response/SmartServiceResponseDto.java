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
package eu.modapto.digitaltwinmanagement.model.response;

import eu.modapto.digitaltwinmanagement.model.ArgumentMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SmartServiceResponse")
public class SmartServiceResponseDto {
    private String id;
    private String serviceCatalogId;
    private String endpoint;
    private String name;
    private String description;
    @Schema(description = "Allows to specifiy how to handle certain input parameters, e.g., have to be provided by the user upon invocation, use constant values, or fetch value from another property of the DT.")
    private Map<String, ArgumentMapping> inputArgumentTypes;
    @Schema(description = "Allows to specifiy how to handle certain output parameters, e.g., to be returned to the user or write value to another property of the DT.")
    private Map<String, ArgumentMapping> outputArgumentTypes;
    @Schema(description = "Input parameters that have to be provided by the user upon invocation.")
    private List<SubmodelElement> actualInputParameters;
    @Schema(description = "Output parameters that will be returned to the user after invocation.")
    private List<SubmodelElement> actualOutputParameters;
}
