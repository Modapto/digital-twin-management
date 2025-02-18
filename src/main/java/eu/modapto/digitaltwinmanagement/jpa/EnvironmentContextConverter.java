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

import de.fraunhofer.iosb.ilt.faaast.service.dataformat.EnvironmentSerializationManager;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.ByteArrayInputStream;
import java.util.Objects;


@Converter(autoApply = false)
public class EnvironmentContextConverter implements AttributeConverter<EnvironmentContext, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(EnvironmentContext environmentContext) {
        if (Objects.isNull(environmentContext)) {
            return null;
        }
        try {
            return EnvironmentSerializationManager.serializerFor(DataFormat.AASX).write(environmentContext);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("error writing AAS to database", e);
        }
    }


    @Override
    public EnvironmentContext convertToEntityAttribute(byte[] data) {
        if (Objects.isNull(data)) {
            return null;
        }
        try {
            return EnvironmentSerializationManager.deserializerFor(DataFormat.AASX).read(new ByteArrayInputStream(data));
        }
        catch (Exception e) {
            throw new IllegalArgumentException("error reading AAS from database", e);
        }
    }

}
