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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import eu.modapto.digitaltwinmanagement.jpa.ListOfSubmodelElementConverter;
import eu.modapto.digitaltwinmanagement.jpa.MapToJsonConverter;
import eu.modapto.digitaltwinmanagement.jpa.ReferenceConverter;
import eu.modapto.digitaltwinmanagement.util.AddressTranslationHelper;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;


@Entity
@Data
@NoArgsConstructor
@SuperBuilder
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InternalSmartService.class, name = "internal"),
        @JsonSubTypes.Type(value = ExternalSmartService.class, name = "external"),
        @JsonSubTypes.Type(value = EmbeddedSmartService.class, name = "embedded")
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "service_type")
public abstract class SmartService {
    @Id
    private String id;
    private String serviceCatalogId;
    private String name;
    private String description;
    @Singular
    @Convert(converter = ListOfSubmodelElementConverter.class)
    @Lob
    private List<SubmodelElement> inputParameters;
    @Singular
    @Convert(converter = ListOfSubmodelElementConverter.class)
    @Lob
    private List<SubmodelElement> outputParameters;

    @ElementCollection
    @Builder.Default
    private Map<String, ArgumentMapping> inputArgumentTypes = new HashMap<>();

    @ElementCollection
    @Builder.Default
    private Map<String, ArgumentMapping> outputArgumentTypes = new HashMap<>();

    @JsonIgnore
    @Convert(converter = ReferenceConverter.class)
    private Reference reference;

    @Convert(converter = MapToJsonConverter.class)
    @Builder.Default
    protected Map<String, Object> properties = new HashMap<>();

    @ManyToOne
    @JoinColumn(name = "module_id")
    @JsonIgnore
    private Module module;

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


    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
