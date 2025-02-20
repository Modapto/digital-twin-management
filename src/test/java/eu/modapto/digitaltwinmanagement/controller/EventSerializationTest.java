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
package eu.modapto.digitaltwinmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iosb.ilt.faaast.service.model.exception.ValueFormatException;
import de.fraunhofer.iosb.ilt.faaast.service.model.value.Datatype;
import de.fraunhofer.iosb.ilt.faaast.service.model.value.PropertyValue;
import de.fraunhofer.iosb.ilt.faaast.service.model.value.SubmodelElementCollectionValue;
import eu.modapto.digitaltwinmanagement.model.event.ModuleCreatedEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleDeletedEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleUpdatedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceFinishedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceInvokedEvent;
import eu.modapto.digitaltwinmanagement.model.event.payload.ModuleDetailsPayload;
import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceFinishedPayload;
import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceInvokedPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;


@SpringBootTest
@ContextConfiguration
class EventSerializationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventSerializationTest.class);
    private static final String PATH_EVENT = "event";
    private static final String EVENT_MODULE_CREATED_FILENAME = "module-created.json";
    private static final String EVENT_MODULE_DELETED_FILENAME = "module-deleted.json";
    private static final String EVENT_MODULE_UPDATED_FILENAME = "module-updated.json";
    private static final String EVENT_SERVICE_INVOKED_FILENAME = "service-invoked.json";
    private static final String EVENT_SERVICE_FINISHED_FILENAME = "service-finished.json";

    private static final ModuleCreatedEvent EVENT_MODULE_CREATED = ModuleCreatedEvent.builder()
            .timestamp(LocalDateTime.of(2025, 1, 30, 14, 12))
            .payload(ModuleDetailsPayload.builder()
                    .moduleId(1)
                    .endpoint("http://example.org/api/v3.0")
                    .build())
            .build();

    private static final ModuleDeletedEvent EVENT_MODULE_DELETED = ModuleDeletedEvent.builder()
            .moduleId(1)
            .timestamp(LocalDateTime.of(2025, 1, 30, 14, 12))
            .build();

    private static final ModuleUpdatedEvent EVENT_MODULE_UPDATED = ModuleUpdatedEvent.builder()
            .timestamp(LocalDateTime.of(2025, 1, 30, 14, 12))
            .payload(ModuleDetailsPayload.builder()
                    .moduleId(1)
                    .endpoint("http://example.org/api/v3.0")
                    .build())
            .build();
    private static SmartServiceInvokedEvent EVENT_SERVICE_INVOKED;
    private static SmartServiceFinishedEvent EVENT_SERVICE_FINISHED;

    @Autowired
    private ObjectMapper mapper;

    @BeforeAll
    static void init() throws ValueFormatException {
        EVENT_SERVICE_INVOKED = SmartServiceInvokedEvent.builder()
                .timestamp(LocalDateTime.of(2025, 1, 30, 14, 12))
                .moduleId(1)
                .payload(SmartServiceInvokedPayload.builder()
                        .serviceId(2)
                        .serviceCatalogId("my-service-catalog-id")
                        .name("ExampleService")
                        .endpoint("http://example.org/api/v3.0/xxxxx/foo")
                        .inputArgument("intValue", PropertyValue.of(Datatype.INT, "42"))
                        .inputArgument("collection1", SubmodelElementCollectionValue.builder()
                                .value("stringValue", PropertyValue.of(Datatype.STRING, "foo"))
                                .value("doubleValue", PropertyValue.of(Datatype.DOUBLE, "11.22"))
                                .build())
                        .build())
                .build();
        EVENT_SERVICE_FINISHED = SmartServiceFinishedEvent.builder()
                .timestamp(LocalDateTime.of(2025, 1, 30, 14, 12))
                .moduleId(1)
                .payload(SmartServiceFinishedPayload.builder()
                        .serviceId(2)
                        .serviceCatalogId("my-service-catalog-id")
                        .name("ExampleService")
                        .endpoint("http://example.org/api/v3.0/xxxxx/foo")
                        .success(true)
                        .outputArgument("intValue", PropertyValue.of(Datatype.INT, "42"))
                        .outputArgument("collection1", SubmodelElementCollectionValue.builder()
                                .value("stringValue", PropertyValue.of(Datatype.STRING, "foo"))
                                .value("doubleValue", PropertyValue.of(Datatype.DOUBLE, "11.22"))
                                .build())
                        .build())
                .build();
    }


    @Test
    void serializeModuleCreatedEvent() throws JSONException, IOException {
        assertSerialize(EVENT_MODULE_CREATED, EVENT_MODULE_CREATED_FILENAME);
    }


    @Test
    void serializeModuleDeletedEvent() throws JSONException, IOException {
        assertSerialize(EVENT_MODULE_DELETED, EVENT_MODULE_DELETED_FILENAME);
    }


    @Test
    void serializeModuleUpdatedEvent() throws JSONException, IOException {
        assertSerialize(EVENT_MODULE_UPDATED, EVENT_MODULE_UPDATED_FILENAME);
    }


    @Test
    void serializeServiceInvokeEvent() throws JSONException, IOException {
        assertSerialize(EVENT_SERVICE_INVOKED, EVENT_SERVICE_INVOKED_FILENAME);
    }


    @Test
    void serializeServiceFinishedEvent() throws JSONException, IOException {
        assertSerialize(EVENT_SERVICE_FINISHED, EVENT_SERVICE_FINISHED_FILENAME);
    }


    private void assertSerialize(Object event, String filename) throws JSONException, IOException {
        String expected = Files.readString(new ClassPathResource("/" + PATH_EVENT + "/" + filename).getFile().toPath());
        String actual = mapper.writeValueAsString(event);
        LOGGER.info(actual);
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }
}
