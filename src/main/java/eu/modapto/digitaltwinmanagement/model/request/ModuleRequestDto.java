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

import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import eu.modapto.digitaltwinmanagement.deployment.DigitalTwinConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ModuleRequest")
public class ModuleRequestDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "base64-encoded")
    private String aas;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "JSON")
    private DataFormat format = DataFormat.JSON;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "DOCKER")
    private DigitalTwinConnectorType type;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<AssetConnectionConfig> assetConnections = new ArrayList<>();
}
