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

import eu.modapto.digitaltwinmanagement.model.ExternalSmartService;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ExternalServiceDetailsResponseDto extends ServiceDetailsResponseDto {
    private RestDetails restDetails;

    @Override
    protected SmartService asSmartServiceInternal() {
        return ExternalSmartService.builder()
                .httpEndpoint(restDetails.getEndpoint())
                .method(restDetails.getMethod())
                .headers(restDetails.getHeaders())
                .outputMapping(restDetails.getOutputMapping())
                .payload(restDetails.getPayload())
                .build();
    }
}
