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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
@Converter(autoApply = false)
public class ListOfSubmodelElementConverter implements AttributeConverter<List<SubmodelElement>, byte[]> {

    @Autowired
    private ObjectMapper mapper;

    @Override
    public byte[] convertToDatabaseColumn(List<SubmodelElement> submodelElements) {
        if (Objects.isNull(submodelElements)) {
            return null;
        }
        try {
            return mapper.writerFor(mapper.getTypeFactory().constructCollectionType(List.class, SubmodelElement.class))
                    .writeValueAsString(submodelElements)
                    .getBytes();
        }
        catch (IOException e) {
            throw new IllegalArgumentException("error writing AAS to database", e);
        }
    }


    @Override
    public List<SubmodelElement> convertToEntityAttribute(byte[] data) {
        if (Objects.isNull(data)) {
            return null;
        }
        try (ByteArrayInputStream temp = new ByteArrayInputStream(data)) {
            return mapper.readValue(new InputStreamReader(temp, StandardCharsets.UTF_8),
                    mapper.getTypeFactory().constructCollectionLikeType(List.class, SubmodelElement.class));
        }
        catch (IOException e) {
            throw new IllegalArgumentException("error reading AAS from database", e);
        }
    }

}
