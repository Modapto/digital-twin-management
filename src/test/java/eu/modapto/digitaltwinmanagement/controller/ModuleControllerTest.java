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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import eu.modapto.digitaltwinmanagement.config.DockerConfig;
import eu.modapto.digitaltwinmanagement.deployment.DigitalTwinConnectorType;
import eu.modapto.digitaltwinmanagement.messagebus.KafkaBridge;
import eu.modapto.digitaltwinmanagement.model.ArgumentMapping;
import eu.modapto.digitaltwinmanagement.model.ArgumentType;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.event.AbstractEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleCreatedEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleDeletedEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleUpdatedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceFinishedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceInvokedEvent;
import eu.modapto.digitaltwinmanagement.model.request.ModuleRequestDto;
import eu.modapto.digitaltwinmanagement.model.request.SmartServiceRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.SmartServiceResponseDto;
import eu.modapto.digitaltwinmanagement.repository.ModuleRepository;
import eu.modapto.digitaltwinmanagement.repository.SmartServiceRepository;
import eu.modapto.digitaltwinmanagement.service.ModuleService;
import eu.modapto.digitaltwinmanagement.service.SmartServiceService;
import eu.modapto.digitaltwinmanagement.util.DockerHelper;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultAssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultEnvironment;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;


@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ContextConfiguration
public class ModuleControllerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleControllerTest.class);
    private static final WireMockServer SERVICE_CATALOG_MOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    private static GenericContainer<?> localDockerRegistry;
    private static String localDockerRegistryUrl;
    private static String internalServiceDockerImage;

    // Resource paths & files
    private static final String EMBEDDED_BOUNCING_BALL_FILENAME = "embedded-bouncing-ball.json";
    private static final String INTERNAL_ADD_FILENAME = "internal-add.json";
    private static final String INTERNAL_ADD_WITH_MAPPINGS_FILENAME = "internal-add-with-mappings.json";
    private static final String EXTERNAL_FILENAME = "external.json";

    private static final String PATH_SERVICE_CATALOG_RESPONSE = "service-catalog-response";
    private static final String PATH_SERVICE_INVOKE = "service-invoke";
    private static final String PATH_SERVICE_RESULT = "service-result";

    // Variables initialized from resources    
    private static String EMBEDDED_BOUNCING_BALL_INVOKE_PAYLOAD;
    private static String EMBEDDED_BOUNCING_BALL_EXPECTED_RESULT;
    private static String EMBEDDED_BOUNCING_BALL_CATALOG_RESPONSE;

    private static String INTERNAL_ADD_INVOKE_PAYLOAD;
    private static String INTERNAL_ADD_WITH_MAPPINGS_INVOKE_PAYLOAD;
    private static String INTERNAL_ADD_EXPECTED_RESULT;
    private static String INTERNAL_ADD_CATALOG_RESPONSE;

    private static String EXTERNAL_INVOKE_PAYLOAD;
    private static String EXTERNAL_EXPECTED_RESULT;
    private static String EXTERNAL_CATALOG_RESPONSE;

    private static final DigitalTwinConnectorType DT_CONNECTOR_TYPE = DigitalTwinConnectorType.INTERNAL;
    private static final String EMBEDDED_SMART_SERVICE_ID = "embedded-1";
    private static final String INTERNAL_SMART_SERVICE_ID = "internal-1";
    private static final String EXTERNAL_SMART_SERVICE_ID = "external-1";

    // RegEx
    private static final String REGEX_LOCATION_HEADER_MODULE = "^/module/(\\d+)$";
    private static final String REGEX_LOCATION_HEADER_SERVICE = "^/service/(\\d+)$";

    // Default AAS model
    private static final String AAS_ID = "http://example.org/aas/1";
    private static final String SUBMODEL_ID = "http://example.org/submodel/1";
    private static final String SUBMODEL_ID_SHORT = "submodel1";
    private static final String PROPERTY_INT_ID_SHORT = "propertyInt";
    private static final String PROPERTY_STRING_ID_SHORT = "propertyString";
    private static final String PROPERTY_DOUBLE_ID_SHORT = "propertyDouble";

    private static final String INTERNAL_SERVICE_IMAGE_NAME = "internal-service-mock";
    private static final long KAFKA_TIMEOUT_IN_MS = 10000;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private KafkaBridge kafkaBridge;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModuleService moduleService;

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private SmartServiceRepository smartServiceRepository;

    @Autowired
    private SmartServiceService smartServiceService;

    @Autowired
    private ObjectMapper mapper;

    @BeforeAll
    private static void init() throws IOException, Exception {
        EMBEDDED_BOUNCING_BALL_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, EMBEDDED_BOUNCING_BALL_FILENAME);
        EMBEDDED_BOUNCING_BALL_EXPECTED_RESULT = readResource(PATH_SERVICE_RESULT, EMBEDDED_BOUNCING_BALL_FILENAME);
        EMBEDDED_BOUNCING_BALL_CATALOG_RESPONSE = readResource(PATH_SERVICE_CATALOG_RESPONSE, EMBEDDED_BOUNCING_BALL_FILENAME);

        INTERNAL_ADD_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, INTERNAL_ADD_FILENAME);
        INTERNAL_ADD_WITH_MAPPINGS_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, INTERNAL_ADD_WITH_MAPPINGS_FILENAME);
        INTERNAL_ADD_EXPECTED_RESULT = readResource(PATH_SERVICE_RESULT, INTERNAL_ADD_FILENAME);
        INTERNAL_ADD_CATALOG_RESPONSE = readResource(PATH_SERVICE_CATALOG_RESPONSE, INTERNAL_ADD_FILENAME);

        EXTERNAL_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, EXTERNAL_FILENAME);
        EXTERNAL_EXPECTED_RESULT = readResource(PATH_SERVICE_RESULT, EXTERNAL_FILENAME);
        EXTERNAL_CATALOG_RESPONSE = readResource(PATH_SERVICE_CATALOG_RESPONSE, EXTERNAL_FILENAME);
        initServiceCatalogueMock();
        initLocalDockerRegistry();
    }


    @BeforeEach
    private void resetMocks() {
        MockitoAnnotations.openMocks(this);
    }


    private static void initServiceCatalogueMock() throws SerializationException, IOException {
        SERVICE_CATALOG_MOCK.start();
        System.setProperty("modapto.service-catalogue.url", SERVICE_CATALOG_MOCK.baseUrl());
        SERVICE_CATALOG_MOCK.stubFor(get(urlPathEqualTo(String.format("/service/%s", EMBEDDED_SMART_SERVICE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(EMBEDDED_BOUNCING_BALL_CATALOG_RESPONSE)));
        SERVICE_CATALOG_MOCK.stubFor(get(urlPathEqualTo(String.format("/service/%s", INTERNAL_SMART_SERVICE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(INTERNAL_ADD_CATALOG_RESPONSE)));
        SERVICE_CATALOG_MOCK.stubFor(get(urlPathEqualTo(String.format("/service/%s", EXTERNAL_SMART_SERVICE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(EXTERNAL_CATALOG_RESPONSE)));
    }


    private static void initLocalDockerRegistry() {
        int dockerRegistryPortInternal = 5000;
        localDockerRegistry = new GenericContainer<>(DockerImageName.parse("registry:2")).withExposedPorts(dockerRegistryPortInternal);
        localDockerRegistry.start();
        localDockerRegistryUrl = "localhost:" + localDockerRegistry.getMappedPort(dockerRegistryPortInternal);
        DockerClient dockerClient = DockerHelper.newClient(DockerConfig.builder()
                .registryUrl(localDockerRegistryUrl)
                .build());
        internalServiceDockerImage = DockerHelper.buildImage(dockerClient, new File("src/test/resources/container/internal-service-mock/Dockerfile"), INTERNAL_SERVICE_IMAGE_NAME);
        DockerHelper.publish(dockerClient, localDockerRegistryUrl, internalServiceDockerImage);
    }


    @AfterAll
    public static void teardown() throws Exception {
        SERVICE_CATALOG_MOCK.stop();
        localDockerRegistry.stop();
    }


    @Test
    public void testCreateModule() throws Exception {
        ModuleRequestDto payload = ModuleRequestDto.builder()
                .aas(asJsonBase64(newDefaultEnvironment()))
                .type(DT_CONNECTOR_TYPE)
                .format(DataFormat.JSON)
                .build();
        MvcResult result = mockMvc.perform(post("/module")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_MODULE)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        long moduleId = extractIdFromLocationHeader(result, REGEX_LOCATION_HEADER_MODULE);
        assertKafkaEvent(moduleCreatedEvent(moduleId));
    }


    @Test
    public void testUpdateModule() throws Exception {
        Environment environment = newDefaultEnvironment();
        MvcResult result = mockMvc.perform(post("/module")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        ModuleRequestDto.builder()
                                .aas(asJsonBase64(environment))
                                .type(DT_CONNECTOR_TYPE)
                                .format(DataFormat.JSON)
                                .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_MODULE)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        long moduleId = extractIdFromLocationHeader(result, REGEX_LOCATION_HEADER_MODULE);
        assertKafkaEvent(moduleCreatedEvent(moduleId));
        environment.getSubmodels().get(0).getSubmodelElements().add(
                new DefaultProperty.Builder()
                        .idShort("newProperty")
                        .value("new")
                        .valueType(DataTypeDefXsd.STRING)
                        .build());
        result = mockMvc.perform(put("/module/" + moduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ModuleRequestDto.builder()
                        .aas(asJsonBase64(environment))
                        .type(DT_CONNECTOR_TYPE)
                        .format(DataFormat.JSON)
                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(moduleService.getModuleById(moduleId).getActualModel().getEnvironment().getSubmodels().get(0).getSubmodelElements().stream()
                .anyMatch(x -> Objects.equals("newProperty", x.getIdShort())));
        assertKafkaEvent(moduleUpdatedEvent(moduleId));
    }


    @Test
    public void testCreateEmbeddedService() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        assertKafkaEvent(moduleCreatedEvent(module.getId()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format("/module/%d/service", module.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                SmartServiceRequestDto.builder()
                                        .serviceCatalogId(EMBEDDED_SMART_SERVICE_ID)
                                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_SERVICE)))
                .andReturn()
                .getResponse();
        SmartServiceResponseDto actual = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        assertInvokeServiceResponse(actual, EMBEDDED_BOUNCING_BALL_INVOKE_PAYLOAD, EMBEDDED_BOUNCING_BALL_EXPECTED_RESULT);
    }


    public static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to find free port", e);
        }
    }


    @Test
    public void testCreateInternalService() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        assertKafkaEvent(moduleCreatedEvent(module.getId()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format("/module/%d/service", module.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                SmartServiceRequestDto.builder()
                                        .serviceCatalogId(INTERNAL_SMART_SERVICE_ID)
                                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_SERVICE)))
                .andReturn()
                .getResponse();
        SmartServiceResponseDto actual = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        assertInvokeServiceResponse(actual, INTERNAL_ADD_INVOKE_PAYLOAD, INTERNAL_ADD_EXPECTED_RESULT);
    }


    @Test
    public void testCreateInternalServiceWithArgumentMappings() throws Exception {
        String dataReferenceIdShort = "myDefaultValue";
        String input2DefaultValue = "2.03";
        Environment environment = newDefaultEnvironment();
        Submodel submodel = environment.getSubmodels().get(0);
        submodel.getSubmodelElements().add(
                new DefaultSubmodelElementCollection.Builder()
                        .idShort(dataReferenceIdShort)
                        .value(new DefaultProperty.Builder()
                                .idShort("input1")
                                .valueType(DataTypeDefXsd.DOUBLE)
                                .value("1.3")
                                .build())
                        .build());
        Reference dataReference = ReferenceBuilder.forSubmodel(submodel, dataReferenceIdShort);
        Module module = moduleService.createModule(Module.builder()
                .providedModel(EnvironmentContext.builder()
                        .environment(environment)
                        .build())
                .type(DT_CONNECTOR_TYPE)
                .build());
        assertKafkaEvent(moduleCreatedEvent(module.getId()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format("/module/%d/service", module.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                SmartServiceRequestDto.builder()
                                        .serviceCatalogId(INTERNAL_SMART_SERVICE_ID)
                                        .inputArgumentTypes(Map.of(
                                                "data", ArgumentMapping.builder()
                                                        .type(ArgumentType.REFERENCE)
                                                        .value(ReferenceHelper.asString(dataReference))
                                                        .build(),
                                                "input2", ArgumentMapping.builder()
                                                        .type(ArgumentType.CONSTANT)
                                                        .value(input2DefaultValue)
                                                        .build()))
                                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_SERVICE)))
                .andReturn()
                .getResponse();
        SmartServiceResponseDto actual = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        assertInvokeServiceResponse(actual, INTERNAL_ADD_WITH_MAPPINGS_INVOKE_PAYLOAD, INTERNAL_ADD_EXPECTED_RESULT);
    }


    @Test
    public void testCreateExternalService() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        assertKafkaEvent(moduleCreatedEvent(module.getId()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format("/module/%d/service", module.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                SmartServiceRequestDto.builder()
                                        .serviceCatalogId(EXTERNAL_SMART_SERVICE_ID)
                                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_SERVICE)))
                .andReturn()
                .getResponse();
        SmartServiceResponseDto actual = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        assertInvokeServiceResponse(actual, EXTERNAL_INVOKE_PAYLOAD, EXTERNAL_EXPECTED_RESULT);
    }


    @Test
    public void testDeleteService() throws Exception {
        String serviceId = "test-delete-service";
        SERVICE_CATALOG_MOCK.stubFor(get(urlPathEqualTo(String.format("/service/%s", serviceId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(EXTERNAL_CATALOG_RESPONSE)));

        Module module = moduleService.createModule(newDefaultModule());
        SmartService service = smartServiceService.addServiceToModule(
                module.getId(),
                SmartServiceRequestDto.builder()
                        .serviceCatalogId(serviceId)
                        .build());
        mockMvc.perform(delete(String.format("/service/%d", service.getId())))
                .andExpect(status().isNoContent());
        assertThat(smartServiceRepository.count()).isZero();
        assertThat(moduleRepository.findAll()).flatExtracting(Module::getServices).extracting(SmartService::getId).doesNotContain(service.getId());
    }


    @Test
    void testDeleteModule() throws Exception {
        String serviceId = "test-delete-module";
        SERVICE_CATALOG_MOCK.stubFor(get(urlPathEqualTo(String.format("/service/%s", serviceId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(EXTERNAL_CATALOG_RESPONSE)));

        Module module = moduleService.createModule(newDefaultModule());
        SmartService service = smartServiceService.addServiceToModule(
                module.getId(),
                SmartServiceRequestDto.builder()
                        .serviceCatalogId(serviceId)
                        .build());
        mockMvc.perform(delete(String.format("/module/%d", module.getId())))
                .andExpect(status().isNoContent());
        assertThat(moduleRepository.count()).isZero();
        assertThat(smartServiceRepository.findAll()).extracting(SmartService::getModule).extracting(Module::getId).doesNotContain(service.getId());
        assertKafkaEvent(
                moduleCreatedEvent(module.getId()),
                moduleDeletedEvent(module.getId()));
    }


    private static Environment newDefaultEnvironment() {
        return new DefaultEnvironment.Builder()
                .assetAdministrationShells(new DefaultAssetAdministrationShell.Builder()
                        .id(AAS_ID)
                        .submodels(ReferenceBuilder.forSubmodel(SUBMODEL_ID))
                        .build())
                .submodels(new DefaultSubmodel.Builder()
                        .id(SUBMODEL_ID)
                        .idShort(SUBMODEL_ID_SHORT)
                        .submodelElements(new DefaultProperty.Builder()
                                .idShort(PROPERTY_INT_ID_SHORT)
                                .value("0")
                                .valueType(DataTypeDefXsd.INT)
                                .build())
                        .submodelElements(new DefaultProperty.Builder()
                                .idShort(PROPERTY_STRING_ID_SHORT)
                                .value("")
                                .valueType(DataTypeDefXsd.INT)
                                .build())
                        .submodelElements(new DefaultProperty.Builder()
                                .idShort(PROPERTY_DOUBLE_ID_SHORT)
                                .value("0.0")
                                .valueType(DataTypeDefXsd.INT)
                                .build())
                        .build())
                .build();
    }


    private static Module newDefaultModule() {
        return Module.builder()
                .providedModel(EnvironmentContext.builder()
                        .environment(newDefaultEnvironment())
                        .build())
                .type(DT_CONNECTOR_TYPE)
                .build();
    }


    private static String asJsonBase64(Environment environment) throws Exception {
        return EncodingHelper.base64Encode(new JsonSerializer().write(environment));
    }


    private static String readResource(String path, String filename) throws IOException {
        return Files.readString(new ClassPathResource("/" + path + "/" + filename).getFile().toPath());
    }


    private void assertInvokeServiceResponse(SmartServiceResponseDto service, String payload, String expectedResult) throws JSONException, JsonProcessingException {
        LOGGER.info("invoking smart service...");
        LOGGER.info("name: {}", service.getName());
        LOGGER.info("url: {}/invoke/$value", service.getEndpoint());
        LOGGER.info("payload: {}", payload);
        String invocationId = "foo-bar";
        ResponseEntity<String> serviceResponse = RestClient.create(service.getEndpoint())
                .post()
                .uri("/invoke/$value")
                .header(Constants.HTTP_HEADER_MODAPTO_INVOCATION_ID, invocationId)
                .body(payload)
                .retrieve()
                .toEntity(String.class);
        LOGGER.info("response from smart service inocation...");
        LOGGER.info("name: {}", service.getName());
        LOGGER.info("status: {}", serviceResponse.getStatusCode());
        LOGGER.info("payload: {}", serviceResponse.getBody());
        assertThat(serviceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONAssert.assertEquals(expectedResult, serviceResponse.getBody(), JSONCompareMode.STRICT);
        assertKafkaEvent(
                serviceInvokedEvent(service, invocationId),
                serviceFinishedEvent(service, invocationId));
    }


    private <T extends AbstractEvent> void assertKafkaEvent(EventInfo... events) throws JsonProcessingException {
        assertKafkaEvents(Arrays.asList(events));
    }


    private <T extends AbstractEvent> void assertKafkaEvents(List<EventInfo> events) throws JsonProcessingException {
        for (var event: events) {
            verify(kafkaTemplate, timeout(KAFKA_TIMEOUT_IN_MS)).send(anyString(), argThat(new ArgumentMatcher<String>() {
                @Override
                public boolean matches(String value) {
                    try {
                        if (event.check.test(mapper.readValue(value, event.type))) {
                            return true;
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            }));
        }
    }


    private EventInfo<ModuleCreatedEvent> moduleCreatedEvent(long moduleId) {
        return new EventInfo<>(ModuleCreatedEvent.class,
                x -> checkCommonEventProperties(ModuleCreatedEvent.class, x)
                        && Objects.equals(Long.toString(moduleId), x.getModuleId())
                        && Objects.equals(moduleId, x.getPayload().getModuleId()));
    }


    private EventInfo<ModuleDeletedEvent> moduleDeletedEvent(long moduleId) {
        return new EventInfo<>(ModuleDeletedEvent.class,
                x -> checkCommonEventProperties(ModuleDeletedEvent.class, x)
                        && Objects.equals(Long.toString(moduleId), x.getModuleId()));
    }


    private EventInfo<ModuleUpdatedEvent> moduleUpdatedEvent(long moduleId) {
        return new EventInfo<>(ModuleUpdatedEvent.class,
                x -> checkCommonEventProperties(ModuleUpdatedEvent.class, x)
                        && Objects.equals(Long.toString(moduleId), x.getModuleId())
                        && Objects.equals(moduleId, x.getPayload().getModuleId()));
    }


    private EventInfo<SmartServiceInvokedEvent> serviceInvokedEvent(SmartServiceResponseDto service, String invocationId) {
        return new EventInfo<>(SmartServiceInvokedEvent.class,
                x -> checkCommonEventProperties(SmartServiceInvokedEvent.class, x)
                        && Objects.equals(service.getId(), x.getPayload().getServiceId())
                        && Objects.equals(service.getServiceCatalogId(), x.getPayload().getServiceCatalogId())
                        && Objects.equals(invocationId, x.getPayload().getInvocationId())
                        && Objects.equals(service.getName(), x.getPayload().getName())
                        && Objects.equals(service.getEndpoint(), x.getPayload().getEndpoint()));
    }


    private EventInfo<SmartServiceFinishedEvent> serviceFinishedEvent(SmartServiceResponseDto service, String invocationId) {
        return new EventInfo<>(SmartServiceFinishedEvent.class,
                x -> checkCommonEventProperties(SmartServiceFinishedEvent.class, x)
                        && Objects.equals(service.getId(), x.getPayload().getServiceId())
                        && Objects.equals(service.getServiceCatalogId(), x.getPayload().getServiceCatalogId())
                        && Objects.equals(invocationId, x.getPayload().getInvocationId())
                        && Objects.equals(service.getName(), x.getPayload().getName())
                        && Objects.equals(service.getEndpoint(), x.getPayload().getEndpoint()));
    }


    private <T extends AbstractEvent> boolean checkCommonEventProperties(Class<T> type, T event) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            AbstractEvent eventType = constructor.newInstance();
            return Objects.equals(eventType.getEventType(), event.getEventType())
                    && Objects.equals(eventType.getPriority(), event.getPriority())
                    && Objects.equals(eventType.getSourceComponent(), event.getSourceComponent())
                    && Objects.equals(eventType.getTopic(), event.getTopic());

        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException("error checking common event properties for type " + type.getSimpleName(), e);
        }
    }


    private Optional<Integer> findEventMessage(List<String> messages, EventInfo event) {
        for (int i = 0; i < messages.size(); i++) {
            try {
                if (event.check.test(mapper.readValue(messages.get(i), event.type))) {
                    return Optional.of(i);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                // ignore
            }
        }
        return Optional.empty();
    }


    private long extractIdFromLocationHeader(MvcResult result, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(result.getResponse().getHeader(HttpHeaders.LOCATION));
        if (!matcher.matches()) {
            fail(String.format("invalid response header (name: %s, value: %s)", HttpHeaders.LOCATION, result.getResponse().getHeader(HttpHeaders.LOCATION)));
        }
        return Long.parseLong(matcher.group(1));
    }

    private class EventInfo<T extends AbstractEvent> {
        Class<T> type;
        Predicate<T> check;

        EventInfo(Class<T> type, Predicate<T> check) {
            this.type = type;
            this.check = check;
        }
    }
}
