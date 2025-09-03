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
package eu.modapto.digitaltwinmanagement.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Transient;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Entity
@DiscriminatorValue("Embedded")
public class EmbeddedSmartService extends SmartService {

    private static final String PROPERTY_RETURN_RESULTS_FOR_EACH_STEP = "returnResultsForEachStep";
    private static final String PROPERTY_INITIAL_ARGUMENTS = "initialArguments";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Lob
    private byte[] fmu;

    public byte[] getFmu() {
        return fmu;
    }


    public void setFmu(byte[] fmu) {
        this.fmu = fmu;
    }


    @Transient
    @JsonIgnore
    public Optional<Boolean> isReturnResultsForEachStep() {
        if (Objects.nonNull(properties) && properties.containsKey(PROPERTY_RETURN_RESULTS_FOR_EACH_STEP)) {
            return Optional.of(Boolean.parseBoolean(Objects.toString(properties.get(PROPERTY_RETURN_RESULTS_FOR_EACH_STEP).toString())));
        }
        return Optional.empty();
    }


    @Transient
    @JsonIgnore
    public Optional<Map<String, String>> getInitialArguments() {
        if (Objects.nonNull(properties) && properties.containsKey(PROPERTY_INITIAL_ARGUMENTS)) {
            return Optional.of(MAPPER.convertValue(properties.get(PROPERTY_INITIAL_ARGUMENTS), new TypeReference<Map<String, String>>() {}));
        }
        return Optional.empty();
    }
}
