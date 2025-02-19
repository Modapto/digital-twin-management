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
package eu.modapto.digitaltwinmanagement.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.SerializationException;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.json.ValueOnlyJsonSerializer;
import de.fraunhofer.iosb.ilt.faaast.service.model.exception.UnsupportedContentModifierException;
import de.fraunhofer.iosb.ilt.faaast.service.model.value.ElementValue;
import java.io.IOException;
import java.util.Map;


public class ElementValueMapSerializer extends JsonSerializer<Map<String, ElementValue>> {
    private final ValueOnlyJsonSerializer serializer;

    public ElementValueMapSerializer() {
        serializer = new ValueOnlyJsonSerializer();
    }


    @Override
    public void serialize(Map<String, ElementValue> data, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        try {
            generator.writeRawValue(serializer.write(data));
        }
        catch (SerializationException | UnsupportedContentModifierException e) {
            throw new JsonMappingException(generator, String.format("Failed to serialize List<SubmodelElement> as valueOnly (reason: %s)", e.getMessage()), e);
        }
    }
}
