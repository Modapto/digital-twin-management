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
package eu.modapto.digitaltwinmanagement.jpa;

import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionConfig;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.json.JsonApiDeserializer;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.json.JsonApiSerializer;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import java.util.Objects;


@Converter(autoApply = false)
public class AssetConnectionConfigListConverter implements AttributeConverter<List<AssetConnectionConfig>, String> {

    @Override
    public String convertToDatabaseColumn(List<AssetConnectionConfig> assetConnections) {
        try {
            return new JsonApiSerializer().write(assetConnections);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("error writing asset connections to database", e);
        }
    }


    @Override
    public List<AssetConnectionConfig> convertToEntityAttribute(String json) {
        if (Objects.isNull(json)) {
            return List.of();
        }
        try {
            return new JsonApiDeserializer().readList(json, AssetConnectionConfig.class);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("error reading asset connections from database", e);
        }
    }

}
