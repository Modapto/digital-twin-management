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
package eu.modapto.digitaltwinmanagement;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static eu.modapto.digitaltwinmanagement.util.Constants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.EnvironmentSerializationManager;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.PortHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.config.SecurityConfig;
import eu.modapto.digitaltwinmanagement.config.TestConfig;
import eu.modapto.digitaltwinmanagement.mapper.SmartServiceMapper;
import eu.modapto.digitaltwinmanagement.messagebus.KafkaBridge;
import eu.modapto.digitaltwinmanagement.model.ArgumentMapping;
import eu.modapto.digitaltwinmanagement.model.ArgumentType;
import eu.modapto.digitaltwinmanagement.model.InternalSmartService;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.event.AbstractEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleCreatedEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleDeletedEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleUpdatedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceAssignedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceFinishedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceInvokedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceUnassignedEvent;
import eu.modapto.digitaltwinmanagement.model.request.ModuleRequestDto;
import eu.modapto.digitaltwinmanagement.model.request.SmartServiceRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.SmartServiceResponseDto;
import eu.modapto.digitaltwinmanagement.repository.ModuleRepository;
import eu.modapto.digitaltwinmanagement.repository.SmartServiceRepository;
import eu.modapto.digitaltwinmanagement.service.ModuleService;
import eu.modapto.digitaltwinmanagement.service.SmartServiceService;
import eu.modapto.digitaltwinmanagement.util.DockerHelper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetKind;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultAssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultAssetInformation;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultBlob;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultEnvironment;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.KeycloakBuilder;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class DeploymentTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentTest.class);
    private static final WireMockServer SERVICE_CATALOG_MOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    private static GenericContainer localDockerRegistry;
    private static KeycloakContainer keycloak;

    private static String localDockerRegistryUrl;

    // Variables initialized from resources    
    private static String EMBEDDED_BOUNCING_BALL_INVOKE_PAYLOAD;
    private static String EMBEDDED_BOUNCING_BALL_EXPECTED_RESULT;
    private static String EMBEDDED_BOUNCING_BALL_SINGLE_RESULT_WITH_ARGUMENTS_EXPECTED_RESULT;
    private static String EMBEDDED_BOUNCING_BALL_CATALOG_RESPONSE;

    private static String INTERNAL_ADD_INVOKE_PAYLOAD;
    private static String INTERNAL_ADD_WITH_MAPPINGS_INVOKE_PAYLOAD;
    private static String INTERNAL_ADD_WITH_BLOB_INVOKE_PAYLOAD;
    private static String INTERNAL_ADD_EXPECTED_RESULT;
    private static String INTERNAL_ADD_CATALOG_RESPONSE;
    private static String INTERNAL_ADD_WITH_BLOB_CATALOG_RESPONSE;

    private static String EXTERNAL_INVOKE_PAYLOAD;
    private static String EXTERNAL_EXPECTED_RESULT;
    private static String EXTERNAL_CATALOG_RESPONSE;

    private static DockerClient dockerClient;

    private static TestConfig testConfig;

    @Autowired
    public void setTestConfig(TestConfig testConfig) {
        DeploymentTest.testConfig = testConfig;
    }

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private KafkaBridge kafkaBridge;

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @Autowired
    private DigitalTwinManagementConfig config;

    @Autowired
    private SecurityConfig securityConfig;

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

    private static String token;

    private static Jwt jwtToken;

    private static boolean initialized = false;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) throws IOException {
        initSecurity();
        registry.add("dt-management.events.mqtt.port", () -> PortHelper.findFreePort());
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> keycloak.getAuthServerUrl() + "/realms/" + KEYCLOAK_REALM);
    }


    @PostConstruct
    void init() throws Exception {
        if (initialized) {
            return;
        }
        config.setPort(port);
        EMBEDDED_BOUNCING_BALL_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, EMBEDDED_BOUNCING_BALL_FILENAME);
        EMBEDDED_BOUNCING_BALL_EXPECTED_RESULT = readResource(PATH_SERVICE_RESULT, EMBEDDED_BOUNCING_BALL_FILENAME);
        EMBEDDED_BOUNCING_BALL_SINGLE_RESULT_WITH_ARGUMENTS_EXPECTED_RESULT = readResource(PATH_SERVICE_RESULT, EMBEDDED_BOUNCING_BALL_SINGLE_RESULT_WITH_ARGUMENTS_FILENAME);
        EMBEDDED_BOUNCING_BALL_CATALOG_RESPONSE = readResource(PATH_SERVICE_CATALOG_RESPONSE, EMBEDDED_BOUNCING_BALL_FILENAME);

        INTERNAL_ADD_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, INTERNAL_ADD_FILENAME);
        INTERNAL_ADD_WITH_MAPPINGS_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, INTERNAL_ADD_WITH_MAPPINGS_FILENAME);
        INTERNAL_ADD_WITH_BLOB_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, INTERNAL_ADD_WITH_BLOB_FILENAME);

        INTERNAL_ADD_EXPECTED_RESULT = readResource(PATH_SERVICE_RESULT, INTERNAL_ADD_FILENAME);
        INTERNAL_ADD_CATALOG_RESPONSE = readResource(PATH_SERVICE_CATALOG_RESPONSE, INTERNAL_ADD_FILENAME);
        INTERNAL_ADD_WITH_BLOB_CATALOG_RESPONSE = readResource(PATH_SERVICE_CATALOG_RESPONSE, INTERNAL_ADD_WITH_BLOB_FILENAME);

        EXTERNAL_INVOKE_PAYLOAD = readResource(PATH_SERVICE_INVOKE, EXTERNAL_FILENAME);
        EXTERNAL_EXPECTED_RESULT = readResource(PATH_SERVICE_RESULT, EXTERNAL_FILENAME);
        EXTERNAL_CATALOG_RESPONSE = readResource(PATH_SERVICE_CATALOG_RESPONSE, EXTERNAL_FILENAME);
        dockerClient = DockerHelper.newClient();
        initLocalDockerRegistry();
        initServiceCatalogueMock();
        initialized = true;
    }


    @BeforeEach
    void resetMocks() {
        MockitoAnnotations.openMocks(this);
    }


    private void initLocalDockerRegistry() {
        localDockerRegistry = new GenericContainer<>(DockerImageName.parse(testConfig.getLocalDockerRegistryImage()))
                .withExposedPorts(testConfig.getLocalDockerRegistryInternalPort())
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forHttp("/v2/").forStatusCode(200));
        if (!StringHelper.isBlank(config.getDockerNetwork())) {
            localDockerRegistry.withNetworkMode(config.getDockerNetwork());
        }
        localDockerRegistry.setPortBindings(List.of(testConfig.getLocalDockerRegistryExternalPort() + ":" + testConfig.getLocalDockerRegistryInternalPort()));
        localDockerRegistry.start();
        localDockerRegistryUrl = localDockerRegistry.getHost() + ":" + testConfig.getLocalDockerRegistryExternalPort();
        LOGGER.info("local registry started: {}", localDockerRegistryUrl);
        createAndLocallyPublishDockerImage(INTERNAL_SERVICE_DOCKERFILE, INTERNAL_SERVICE_IMAGE_NAME);
        createAndLocallyPublishDockerImage(INTERNAL_SERVICE_WITH_BLOB_DOCKERFILE, INTERNAL_SERVICE_WITH_BLOB_IMAGE_NAME);
    }


    private void initServiceCatalogueMock() throws SerializationException, IOException {
        SERVICE_CATALOG_MOCK.start();
        config.setServiceCatalogueHost(SERVICE_CATALOG_MOCK.baseUrl());
        mockServiceInCatalog(EMBEDDED_SMART_SERVICE_ID, EMBEDDED_BOUNCING_BALL_CATALOG_RESPONSE);
        mockServiceInCatalog(INTERNAL_SMART_SERVICE_ID, INTERNAL_ADD_CATALOG_RESPONSE.replace("${registry.url}", localDockerRegistryUrl));
        mockServiceInCatalog(INTERNAL_SMART_SERVICE_WITH_BLOB_ID, INTERNAL_ADD_WITH_BLOB_CATALOG_RESPONSE.replace("${registry.url}", localDockerRegistryUrl));
        mockServiceInCatalog(EXTERNAL_SMART_SERVICE_ID, EXTERNAL_CATALOG_RESPONSE);
    }


    private void mockServiceInCatalog(String serviceId, String responsePayload) throws JsonProcessingException {
        SERVICE_CATALOG_MOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(String.format(config.getServiceCataloguePath(), serviceId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(responsePayload)));
    }


    private static void initSecurity() throws IOException {
        keycloak = new KeycloakContainer()
                .withRealmImportFile(KEYCLOAK_CONFIG_FILE);
        keycloak.start();
        token = KeycloakBuilder.builder()
                .serverUrl(keycloak.getAuthServerUrl())
                .realm(KEYCLOAK_REALM)
                .clientId(KEYCLOAK_CLIENT_ID)
                .clientSecret(KEYCLOAK_CLIENT_SECRET)
                .username(KEYCLOAK_USERNAME)
                .password(KEYCLOAK_PASSWORD)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build()
                .tokenManager().getAccessToken().getToken();
        jwtToken = NimbusJwtDecoder.withIssuerLocation(keycloak.getAuthServerUrl() + "/realms/" + KEYCLOAK_REALM)
                .build()
                .decode(token);
    }


    private void createAndLocallyPublishDockerImage(String dockerFile, String imageName) {
        String internalServiceDockerImage = DockerHelper.buildImage(
                dockerClient,
                new File(dockerFile),
                imageName);
        DockerHelper.publish(
                DockerHelper.newClient("http://" + localDockerRegistryUrl),
                localDockerRegistryUrl,
                internalServiceDockerImage,
                imageName);
    }


    @AfterAll
    static void teardown() throws Exception {
        if (Objects.nonNull(dockerClient)) {
            dockerClient.close();
        }
        SERVICE_CATALOG_MOCK.stop();
    }


    @AfterEach
    void cleanUpDockerContainers() {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        for (Container container: containers) {
            if (Objects.equals(config.getDtDockerImage(), container.getImage())) {
                try {
                    DockerHelper.removeContainer(dockerClient, container.getId());
                    LOGGER.info("cleaned up container {}", container.getId());
                }
                catch (DockerException e) {
                    LOGGER.debug("exception cleaning up module container {}", container.getId(), e);
                }
            }
        }
        smartServiceService.getAllSmartServices().stream()
                .filter(InternalSmartService.class::isInstance)
                .map(InternalSmartService.class::cast)
                .filter(x -> Objects.nonNull(x.getContainerId()))
                .forEach(x -> {
                    try {
                        dockerClient.stopContainerCmd(x.getContainerId()).exec();
                        dockerClient.removeContainerCmd(x.getContainerId()).exec();
                        LOGGER.debug("cleaned up container {}", x.getContainerId());
                    }
                    catch (DockerException e) {
                        LOGGER.debug("exception cleaning up internal smart service container {}", x.getContainerId(), e);
                    }
                });
        dockerClient.listVolumesCmd().exec().getVolumes().stream()
                .filter(x -> x.getName().startsWith("vol-"))
                .forEach(x -> {
                    try {
                        DockerHelper.removeVolume(dockerClient, x.getName());
                        LOGGER.debug("cleaned up volume {}", x.getName());
                    }
                    catch (Exception e) {
                        LOGGER.debug("exception cleaning up volume {}", x.getName(), e);
                    }
                });
    }


    @Test
    void testAccessWithoutToken() throws Exception {
        mockMvc.perform(get(REST_PATH_MODULES))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void testAccessWithInvalidToken() throws Exception {
        mockMvc.perform(get(REST_PATH_MODULES)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + INVALID_TOKEN))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void testCreateModuleFromAASX() throws Exception {
        String moduleName = "MyNewModuleAASX";
        ModuleRequestDto payload = ModuleRequestDto.builder()
                .name(moduleName)
                .aas(asAasxBase64(newDefaultEnvironment()))
                .type(testConfig.getDtDeplyomentType())
                .format(DataFormat.AASX)
                .build();

        MvcResult result = mockMvc.perform(post(REST_PATH_MODULES)
                .header(HttpHeaders.AUTHORIZATION, getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_MODULE)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        String moduleId = extractIdFromLocationHeader(result, REGEX_LOCATION_HEADER_MODULE);
        assertKafkaEvent(moduleCreatedEvent(moduleId, moduleName));
    }


    @Test
    void testCreateModule() throws Exception {
        String moduleName = "MyNewModule";
        ModuleRequestDto payload = ModuleRequestDto.builder()
                .name(moduleName)
                .aas(asJsonBase64(newDefaultEnvironment()))
                .type(testConfig.getDtDeplyomentType())
                .format(DataFormat.JSON)
                .build();

        MvcResult result = mockMvc.perform(post(REST_PATH_MODULES)
                .header(HttpHeaders.AUTHORIZATION, getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_MODULE)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        String moduleId = extractIdFromLocationHeader(result, REGEX_LOCATION_HEADER_MODULE);
        assertKafkaEvent(moduleCreatedEvent(moduleId, moduleName));
    }


    @Test
    void testUpdateModuleWithExternalServicePresent() throws Exception {
        Environment environment = newDefaultEnvironment();
        MvcResult result = mockMvc.perform(post(REST_PATH_MODULES)
                .header(HttpHeaders.AUTHORIZATION, getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        ModuleRequestDto.builder()
                                .aas(asJsonBase64(environment))
                                .name(AAS_ID_SHORT)
                                .type(testConfig.getDtDeplyomentType())
                                .format(DataFormat.JSON)
                                .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_MODULE)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        String moduleId = extractIdFromLocationHeader(result, REGEX_LOCATION_HEADER_MODULE);
        assertKafkaEvent(moduleCreatedEvent(moduleId, AAS_ID_SHORT));
        // assign service
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, moduleId) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
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
        assertKafkaEvent(serviceAssignedEvent(actual));
        assertInvokeServiceResponse(actual, EXTERNAL_INVOKE_PAYLOAD, EXTERNAL_EXPECTED_RESULT);

        // update model
        environment.getSubmodels().get(0).getSubmodelElements().add(
                new DefaultProperty.Builder()
                        .idShort("newProperty")
                        .value("new")
                        .valueType(DataTypeDefXsd.STRING)
                        .build());
        result = mockMvc.perform(put(String.format(REST_PATH_MODULE_TEMPLATE, moduleId))
                .header(HttpHeaders.AUTHORIZATION, getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ModuleRequestDto.builder()
                        .aas(asJsonBase64(environment))
                        .type(testConfig.getDtDeplyomentType())
                        .format(DataFormat.JSON)
                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(moduleService.getModuleById(moduleId).getActualModel().getEnvironment().getSubmodels().get(0).getSubmodelElements().stream()
                .anyMatch(x -> Objects.equals("newProperty", x.getIdShort())));
        assertKafkaEvent(moduleUpdatedEvent(moduleId, AAS_ID_SHORT));

        // assert service invocation still works
        assertInvokeServiceResponse(actual, EXTERNAL_INVOKE_PAYLOAD, EXTERNAL_EXPECTED_RESULT);
    }


    @Test
    void testUpdateModule() throws Exception {
        Environment environment = newDefaultEnvironment();
        MvcResult result = mockMvc.perform(post(REST_PATH_MODULES)
                .header(HttpHeaders.AUTHORIZATION, getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        ModuleRequestDto.builder()
                                .aas(asJsonBase64(environment))
                                .name(AAS_ID_SHORT)
                                .type(testConfig.getDtDeplyomentType())
                                .format(DataFormat.JSON)
                                .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_MODULE)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        String moduleId = extractIdFromLocationHeader(result, REGEX_LOCATION_HEADER_MODULE);
        assertKafkaEvent(moduleCreatedEvent(moduleId, AAS_ID_SHORT));
        environment.getSubmodels().get(0).getSubmodelElements().add(
                new DefaultProperty.Builder()
                        .idShort("newProperty")
                        .value("new")
                        .valueType(DataTypeDefXsd.STRING)
                        .build());
        result = mockMvc.perform(put(String.format(REST_PATH_MODULE_TEMPLATE, moduleId))
                .header(HttpHeaders.AUTHORIZATION, getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ModuleRequestDto.builder()
                        .aas(asJsonBase64(environment))
                        .type(testConfig.getDtDeplyomentType())
                        .format(DataFormat.JSON)
                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(moduleService.getModuleById(moduleId).getActualModel().getEnvironment().getSubmodels().get(0).getSubmodelElements().stream()
                .anyMatch(x -> Objects.equals("newProperty", x.getIdShort())));
        assertKafkaEvent(moduleUpdatedEvent(moduleId, AAS_ID_SHORT));
    }


    @Test
    void testCreateEmbeddedService() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        assertKafkaEvent(moduleCreatedEvent(module.getId(), module.getName()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
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
        assertKafkaEvent(serviceAssignedEvent(actual));
        assertInvokeServiceResponse(actual, EMBEDDED_BOUNCING_BALL_INVOKE_PAYLOAD, EMBEDDED_BOUNCING_BALL_EXPECTED_RESULT);
    }


    @Test
    void testCreateEmbeddedService_withSingleResult_withInitialArguments() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        assertKafkaEvent(moduleCreatedEvent(module.getId(), module.getName()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                SmartServiceRequestDto.builder()
                                        .serviceCatalogId(EMBEDDED_SMART_SERVICE_ID)
                                        .properties(Map.of(
                                                "returnResultsForEachStep", false,
                                                "initialArguments", Map.of(
                                                        "g", -20)))
                                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_SERVICE)))
                .andReturn()
                .getResponse();
        SmartServiceResponseDto actual = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        assertKafkaEvent(serviceAssignedEvent(actual));
        assertInvokeServiceResponse(actual, EMBEDDED_BOUNCING_BALL_INVOKE_PAYLOAD, EMBEDDED_BOUNCING_BALL_SINGLE_RESULT_WITH_ARGUMENTS_EXPECTED_RESULT);
    }


    @Test
    void testCreateInternalService() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        assertKafkaEvent(moduleCreatedEvent(module.getId(), module.getName()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
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
        assertKafkaEvent(serviceAssignedEvent(actual));
        assertInvokeServiceResponse(actual, INTERNAL_ADD_INVOKE_PAYLOAD, INTERNAL_ADD_EXPECTED_RESULT);
    }


    @Test
    void testCreateInternalServiceWithArgumentMappings() throws Exception {
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
                .type(testConfig.getDtDeplyomentType())
                .build());
        assertKafkaEvent(moduleCreatedEvent(module.getId(), module.getName()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
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
        assertKafkaEvent(serviceAssignedEvent(actual));
        assertInvokeServiceResponse(actual, INTERNAL_ADD_WITH_MAPPINGS_INVOKE_PAYLOAD, INTERNAL_ADD_EXPECTED_RESULT);
    }


    @Test
    void testCreateInternalServiceWithBlob() throws Exception {
        String dataReferenceIdShort = "externalInput2";
        Environment environment = newDefaultEnvironment();
        Submodel submodel = environment.getSubmodels().get(0);
        submodel.getSubmodelElements().add(
                new DefaultBlob.Builder()
                        .idShort(dataReferenceIdShort)
                        .value("{\"data\": 2.03}".getBytes())
                        .build());
        Reference dataReference = ReferenceBuilder.forSubmodel(submodel, dataReferenceIdShort);
        Module module = moduleService.createModule(Module.builder()
                .providedModel(EnvironmentContext.builder()
                        .environment(environment)
                        .build())
                .type(testConfig.getDtDeplyomentType())
                .build());
        assertKafkaEvent(moduleCreatedEvent(module.getId(), module.getName()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                SmartServiceRequestDto.builder()
                                        .serviceCatalogId(INTERNAL_SMART_SERVICE_WITH_BLOB_ID)
                                        .inputArgumentTypes(Map.of(
                                                "input2", ArgumentMapping.builder()
                                                        .type(ArgumentType.REFERENCE)
                                                        .value(ReferenceHelper.asString(dataReference))
                                                        .build()))
                                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(REGEX_LOCATION_HEADER_SERVICE)))
                .andReturn()
                .getResponse();
        SmartServiceResponseDto actual = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        assertInvokeServiceResponse(actual, INTERNAL_ADD_WITH_BLOB_INVOKE_PAYLOAD, INTERNAL_ADD_EXPECTED_RESULT);
    }


    @Test
    void testCreateExternalService() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        assertKafkaEvent(moduleCreatedEvent(module.getId(), module.getName()));
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
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
        assertKafkaEvent(serviceAssignedEvent(actual));
        assertInvokeServiceResponse(actual, EXTERNAL_INVOKE_PAYLOAD, EXTERNAL_EXPECTED_RESULT);
    }


    @Test
    void testCreateModuleWithMultipleServices() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        String originalModuleId = module.getId();
        assertKafkaEvent(moduleCreatedEvent(module.getId(), module.getName()));
        // first service
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
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
        SmartServiceResponseDto service1 = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        assertKafkaEvent(serviceAssignedEvent(service1));
        assertInvokeServiceResponse(service1, EXTERNAL_INVOKE_PAYLOAD, EXTERNAL_EXPECTED_RESULT);
        // second service
        response = mockMvc.perform(
                post(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()) + REST_PATH_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, getBearerToken())
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
        SmartServiceResponseDto service2 = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        assertKafkaEvent(serviceAssignedEvent(service2));
        assertInvokeServiceResponse(service2, EXTERNAL_INVOKE_PAYLOAD, EXTERNAL_EXPECTED_RESULT);
        // validate moduleId has not changed
        List<Module> modules = moduleService.getAllModules();
        assertThat(modules.size()).isEqualTo(1);
        Module actualModule = modules.get(0);
        assertThat(actualModule.getServices().size()).isEqualTo(2);
        assertThat(actualModule.getId()).isEqualTo(originalModuleId);
        // validate invoking first service still works
        assertInvokeServiceResponse(service1, EXTERNAL_INVOKE_PAYLOAD, EXTERNAL_EXPECTED_RESULT);
        assertInvokeServiceResponse(service2, EXTERNAL_INVOKE_PAYLOAD, EXTERNAL_EXPECTED_RESULT);
    }


    @Test
    void testDeleteService() throws Exception {
        String serviceId = "test-delete-service";
        mockServiceInCatalog(serviceId, EXTERNAL_CATALOG_RESPONSE);
        Module module = moduleService.createModule(newDefaultModule());

        SmartService service = smartServiceService.addServiceToModule(
                module.getId(),
                SmartServiceRequestDto.builder()
                        .serviceCatalogId(serviceId)
                        .build(),
                jwtToken);
        assertKafkaEvent(serviceAssignedEvent(SmartServiceMapper.toDto(service)));
        mockMvc.perform(delete(String.format(REST_PATH_SERVICE_TEMPLATE, service.getId()))
                .header(HttpHeaders.AUTHORIZATION, getBearerToken()))
                .andExpect(status().isNoContent());
        assertKafkaEvent(serviceUnassignedEvent(SmartServiceMapper.toDto(service)));
        assertThat(smartServiceRepository.count()).isZero();
        assertThat(moduleRepository.findAll()).flatExtracting(Module::getServices).extracting(SmartService::getId).doesNotContain(service.getId());
    }


    @Test
    void testDeleteModule() throws Exception {
        String serviceId = "test-delete-module";
        mockServiceInCatalog(serviceId, EXTERNAL_CATALOG_RESPONSE);
        Module module = moduleService.createModule(newDefaultModule());
        SmartService service = smartServiceService.addServiceToModule(
                module.getId(),
                SmartServiceRequestDto.builder()
                        .serviceCatalogId(serviceId)
                        .build(),
                jwtToken);
        mockMvc.perform(delete(String.format(REST_PATH_MODULE_TEMPLATE, module.getId()))
                .header(HttpHeaders.AUTHORIZATION, getBearerToken()))
                .andExpect(status().isNoContent());
        assertThat(moduleRepository.count()).isZero();
        assertThat(smartServiceRepository.findAll()).extracting(SmartService::getModule).extracting(Module::getId).doesNotContain(service.getId());
        assertKafkaEvent(
                moduleCreatedEvent(module.getId(), module.getName()),
                moduleDeletedEvent(module.getId()));
    }


    private static Environment newDefaultEnvironment() {
        return new DefaultEnvironment.Builder()
                .assetAdministrationShells(new DefaultAssetAdministrationShell.Builder()
                        .id(AAS_ID)
                        .idShort(AAS_ID_SHORT)
                        .submodels(ReferenceBuilder.forSubmodel(SUBMODEL_ID))
                        .assetInformation(new DefaultAssetInformation.Builder()
                                .assetKind(AssetKind.INSTANCE)
                                .globalAssetId(GLOBAL_ASSET_ID)
                                .build())
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


    private Module newDefaultModule() {
        return Module.builder()
                .providedModel(EnvironmentContext.builder()
                        .environment(newDefaultEnvironment())
                        .build())
                .type(testConfig.getDtDeplyomentType())
                .build();
    }


    private static String asJsonBase64(Environment environment) throws Exception {
        return EncodingHelper.base64Encode(new JsonSerializer().write(environment));
    }


    private static String asAasxBase64(Environment environment) throws Exception {
        return new String(EncodingHelper.base64Encode(EnvironmentSerializationManager.serializerFor(DataFormat.AASX).write(environment)));
    }


    private static String readResource(String path, String filename) throws IOException {
        return Files.readString(new ClassPathResource("/" + path + "/" + filename).getFile().toPath());
    }


    private void assertInvokeServiceResponse(SmartServiceResponseDto service, String payload, String expectedResult) throws JSONException {
        LOGGER.debug("invoking smart service...");
        LOGGER.debug("name: {}", service.getName());
        LOGGER.debug("url: {}/invoke/$value", service.getEndpoint());
        LOGGER.debug("payload: {}", payload);
        String invocationId = "foo-bar";

        HttpHeaders headers = new HttpHeaders();
        headers.set(HTTP_HEADER_MODAPTO_INVOCATION_ID, invocationId);
        if (securityConfig.isSecureProxyDTs()) {
            headers.set(HttpHeaders.AUTHORIZATION, getBearerToken());
        }
        ResponseEntity<String> serviceResponse = new RestTemplate().exchange(
                new RequestEntity<>(
                        payload,
                        headers,
                        HttpMethod.POST,
                        URI.create(service.getEndpoint() + "/invoke/$value")),
                String.class);
        LOGGER.debug("response from smart service invocation...");
        LOGGER.debug("name: {}", service.getName());
        LOGGER.debug("status: {}", serviceResponse.getStatusCode());
        LOGGER.debug("payload: {}", serviceResponse.getBody());
        assertThat(serviceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONAssert.assertEquals(expectedResult, serviceResponse.getBody(), JSONCompareMode.STRICT);
        assertKafkaEvent(
                serviceInvokedEvent(service, invocationId),
                serviceFinishedEvent(service, invocationId));
    }


    private <T extends AbstractEvent> void assertKafkaEvent(EventInfo... events) {
        assertKafkaEvents(Arrays.asList(events));
    }


    private <T extends AbstractEvent> void assertKafkaEvents(List<EventInfo> events) {
        for (var event: events) {
            verify(kafkaTemplate, timeout(KAFKA_TIMEOUT_IN_MS)).send(anyString(), argThat(new ArgumentMatcher<>() {
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
        Mockito.reset(kafkaTemplate);
    }


    private EventInfo<ModuleCreatedEvent> moduleCreatedEvent(String moduleId, String moduleName) {
        return new EventInfo<>(ModuleCreatedEvent.class,
                x -> checkCommonEventProperties(ModuleCreatedEvent.class, x)
                        && Objects.equals(moduleId, x.getModuleId())
                        && Objects.equals(moduleName, x.getPayload().getName())
                        && Objects.equals(moduleId, x.getPayload().getModuleId()));
    }


    private EventInfo<ModuleDeletedEvent> moduleDeletedEvent(String moduleId) {
        return new EventInfo<>(ModuleDeletedEvent.class,
                x -> checkCommonEventProperties(ModuleDeletedEvent.class, x)
                        && Objects.equals(moduleId, x.getModuleId()));
    }


    private EventInfo<ModuleUpdatedEvent> moduleUpdatedEvent(String moduleId, String moduleName) {
        return new EventInfo<>(ModuleUpdatedEvent.class,
                x -> checkCommonEventProperties(ModuleUpdatedEvent.class, x)
                        && Objects.equals(moduleId, x.getModuleId())
                        && Objects.equals(moduleName, x.getPayload().getName())
                        && Objects.equals(moduleId, x.getPayload().getModuleId()));
    }


    private EventInfo<SmartServiceAssignedEvent> serviceAssignedEvent(SmartServiceResponseDto service) {
        return new EventInfo<>(SmartServiceAssignedEvent.class,
                x -> checkCommonEventProperties(SmartServiceAssignedEvent.class, x)
                        && Objects.equals(service.getId(), x.getPayload().getServiceId())
                        && Objects.equals(service.getServiceCatalogId(), x.getPayload().getServiceCatalogId())
                        && Objects.equals(true, x.getPayload().isSuccess())
                        && Objects.equals(service.getName(), x.getPayload().getName())
                        && Objects.equals(service.getEndpoint(), x.getPayload().getEndpoint()));
    }


    private EventInfo<SmartServiceUnassignedEvent> serviceUnassignedEvent(SmartServiceResponseDto service) {
        return new EventInfo<>(SmartServiceUnassignedEvent.class,
                x -> checkCommonEventProperties(SmartServiceUnassignedEvent.class, x)
                        && Objects.equals(service.getId(), x.getPayload().getServiceId())
                        && Objects.equals(service.getServiceCatalogId(), x.getPayload().getServiceCatalogId())
                        && Objects.equals(true, x.getPayload().isSuccess())
                        && Objects.equals(service.getName(), x.getPayload().getName())
                        && Objects.equals(service.getEndpoint(), x.getPayload().getEndpoint()));
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


    private String extractIdFromLocationHeader(MvcResult result, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(result.getResponse().getHeader(HttpHeaders.LOCATION));
        if (!matcher.matches()) {
            fail(String.format("invalid response header (name: %s, value: %s)", HttpHeaders.LOCATION, result.getResponse().getHeader(HttpHeaders.LOCATION)));
        }
        return matcher.group(1);
    }


    private static String getBearerToken() {
        return BEARER_PREFIX + token;
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
