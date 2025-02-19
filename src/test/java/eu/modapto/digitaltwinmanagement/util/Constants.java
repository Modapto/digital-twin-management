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
package eu.modapto.digitaltwinmanagement.util;

public class Constants {
    // Resource paths & files
    public static final String EMBEDDED_BOUNCING_BALL_FILENAME = "embedded-bouncing-ball.json";
    public static final String INTERNAL_ADD_FILENAME = "internal-add.json";
    public static final String INTERNAL_ADD_WITH_MAPPINGS_FILENAME = "internal-add-with-mappings.json";
    public static final String EXTERNAL_FILENAME = "external.json";

    public static final String PATH_SERVICE_CATALOG_RESPONSE = "service-catalog-response";
    public static final String PATH_SERVICE_INVOKE = "service-invoke";
    public static final String PATH_SERVICE_RESULT = "service-result";

    // Smart Service IDs
    public static final String EMBEDDED_SMART_SERVICE_ID = "embedded-1";
    public static final String INTERNAL_SMART_SERVICE_ID = "internal-1";
    public static final String EXTERNAL_SMART_SERVICE_ID = "external-1";

    // REST
    public static final String REGEX_LOCATION_HEADER_MODULE = "^/modules/(\\d+)$";
    public static final String REGEX_LOCATION_HEADER_SERVICE = "^/services/(\\d+)$";
    public static final String REST_PATH_MODULES = "/modules";
    public static final String REST_PATH_SERVICES = "/services";
    public static final String REST_PATH_MODULE_TEMPLATE = REST_PATH_MODULES + "/%s";
    public static final String REST_PATH_SERVICE_TEMPLATE = REST_PATH_SERVICES + "/%s";

    // AAS model
    public static final String AAS_ID = "http://example.org/aas/1";
    public static final String SUBMODEL_ID = "http://example.org/submodel/1";
    public static final String SUBMODEL_ID_SHORT = "submodel1";
    public static final String PROPERTY_INT_ID_SHORT = "propertyInt";
    public static final String PROPERTY_STRING_ID_SHORT = "propertyString";
    public static final String PROPERTY_DOUBLE_ID_SHORT = "propertyDouble";

    // Docker
    public static final String INTERNAL_SERVICE_IMAGE_NAME = "internal-service-mock";
    public static final String INTERNAL_SERVICE_DOCKERFILE = "src/test/resources/container/internal-service-mock/Dockerfile";

    // Other
    public static final long KAFKA_TIMEOUT_IN_MS = 10000;
    public static final String HTTP_HEADER_MODAPTO_INVOCATION_ID = "X-MODAPTO-Invocation-Id";
}
