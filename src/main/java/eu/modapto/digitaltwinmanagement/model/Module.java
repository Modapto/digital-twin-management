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
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import eu.modapto.digitaltwinmanagement.deployment.DeploymentType;
import eu.modapto.digitaltwinmanagement.exception.ResourceNotFoundException;
import eu.modapto.digitaltwinmanagement.jpa.AssetConnectionConfigListConverter;
import eu.modapto.digitaltwinmanagement.jpa.EnvironmentContextConverter;
import eu.modapto.digitaltwinmanagement.util.AddressTranslationHelper;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Module {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    private int externalPort;

    private String containerId;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<SmartService> services = new ArrayList<>();

    private DeploymentType type;

    @Convert(converter = EnvironmentContextConverter.class)
    @Lob
    private EnvironmentContext providedModel;

    @Convert(converter = EnvironmentContextConverter.class)
    @Lob
    private EnvironmentContext actualModel;

    @Convert(converter = AssetConnectionConfigListConverter.class)
    @Lob
    @Builder.Default
    private List<AssetConnectionConfig> assetConnections = new ArrayList<>();

    @Transient
    @JsonIgnore
    public String getInternalEndpoint() {
        return AddressTranslationHelper.getInternalEndpoint(this);
    }


    @Transient
    @JsonIgnore
    public String getExternalEndpoint() {
        return AddressTranslationHelper.getExternalEndpoint(this);
    }


    public SmartService getServiceById(String serviceId) {
        return services.stream()
                .filter(x -> Objects.equals(x.getId(), serviceId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(String.format(
                        "service not found for module (module id: %s, service id: %s)",
                        id,
                        serviceId)));
    }
}
