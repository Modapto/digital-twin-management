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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.model.serialization.DataFormat;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import eu.modapto.digitaltwinmanagement.deployment.DigitalTwinConnectorType;
import eu.modapto.digitaltwinmanagement.model.EmbeddedSmartService;
import eu.modapto.digitaltwinmanagement.model.ExternalSmartService;
import eu.modapto.digitaltwinmanagement.model.InternalSmartService;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.request.ModuleRequestDto;
import eu.modapto.digitaltwinmanagement.model.request.SmartServiceRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.SmartServiceResponseDto;
import eu.modapto.digitaltwinmanagement.repository.ModuleRepository;
import eu.modapto.digitaltwinmanagement.repository.SmartServiceRepository;
import eu.modapto.digitaltwinmanagement.service.ModuleService;
import eu.modapto.digitaltwinmanagement.service.SmartServiceService;
import java.io.IOException;
import java.nio.file.Files;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultAssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultEnvironment;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;


@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ContextConfiguration
public class ModuleControllerIntegrationTest {
    private static byte[] FMU_BOUNCING_BALL;
    private static String FMU_BOUNCING_BALL_INVOKE_PAYLOAD;
    private static String FMU_BOUNCING_BALL_EXPECTED_RESULT;
    private static final DigitalTwinConnectorType DT_CONNECTOR_TYPE = DigitalTwinConnectorType.INTERNAL;
    private static final long EMBEDDED_SMART_SERVICE_ID = 1;
    private static final long INTERNAL_SMART_SERVICE_ID = 2;
    private static final long EXTERNAL_SMART_SERVICE_ID = 3;
    private static final WireMockServer serviceCatalogueMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

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

    @AfterAll
    public static void teardown() throws Exception {
        serviceCatalogueMock.stop();
    }


    @BeforeAll
    private static void init() throws IOException, Exception {
        FMU_BOUNCING_BALL = Files.readAllBytes(new ClassPathResource("BouncingBall.fmu").getFile().toPath());
        FMU_BOUNCING_BALL_INVOKE_PAYLOAD = Files.readString(new ClassPathResource("fmu-bouncing-ball-invoke-payload.json").getFile().toPath());
        FMU_BOUNCING_BALL_EXPECTED_RESULT = Files.readString(new ClassPathResource("fmu-bouncing-ball-expected-result.json").getFile().toPath());
        initServiceCatalogueMock();
    }


    private static void initServiceCatalogueMock() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        serviceCatalogueMock.start();
        System.setProperty("modapto.service-catalogue.url", serviceCatalogueMock.baseUrl());
        serviceCatalogueMock.stubFor(get(urlPathEqualTo(String.format("/service/%d", EMBEDDED_SMART_SERVICE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(
                                EmbeddedSmartService.builder()
                                        .fmu(FMU_BOUNCING_BALL)
                                        .name("embedded-service")
                                        .build()))));
        serviceCatalogueMock.stubFor(get(urlPathEqualTo(String.format("/service/%d", INTERNAL_SMART_SERVICE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(
                                InternalSmartService.builder()
                                        .name("internal-service")
                                        .build()))));
        serviceCatalogueMock.stubFor(get(urlPathEqualTo(String.format("/service/%d", EXTERNAL_SMART_SERVICE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(
                                ExternalSmartService.builder()
                                        .name("external-service")
                                        .build()))));
    }


    @Test
    public void testCreateModule() throws Exception {
        ModuleRequestDto payload = ModuleRequestDto.builder()
                .aas(asJsonBase64(newDefaultEnvironment()))
                .type(DT_CONNECTOR_TYPE)
                .format(DataFormat.JSON)
                .build();
        mockMvc.perform(post("/module")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("^/module/\\d+$")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }


    @Test
    public void testCreateEmbeddedService() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        MockHttpServletResponse response = mockMvc.perform(
                post(String.format("/module/%d/service", module.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                SmartServiceRequestDto.builder()
                                        .serviceId(EMBEDDED_SMART_SERVICE_ID)
                                        .build())))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("^/service/\\d+$")))
                .andReturn()
                .getResponse();
        SmartServiceResponseDto actual = mapper.readValue(response.getContentAsByteArray(), SmartServiceResponseDto.class);
        ResponseEntity<String> serviceResponse = RestClient.create(actual.getEndpoint())
                .post()
                .uri("/invoke/$value")
                .body(FMU_BOUNCING_BALL_INVOKE_PAYLOAD)
                .retrieve()
                .toEntity(String.class);
        assertThat(serviceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONAssert.assertEquals(FMU_BOUNCING_BALL_EXPECTED_RESULT, serviceResponse.getBody(), JSONCompareMode.STRICT);
    }


    @Test
    public void testDeleteService() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        SmartService service = smartServiceService.addServiceToModule(module.getId(), EMBEDDED_SMART_SERVICE_ID);
        mockMvc.perform(delete(String.format("/service/%d", service.getId())))
                .andExpect(status().isNoContent());
        assertThat(smartServiceRepository.count()).isZero();
        assertThat(moduleRepository.findAll()).flatExtracting(Module::getServices).extracting(SmartService::getId).doesNotContain(service.getId());
    }


    @Test
    void testDeleteModule() throws Exception {
        Module module = moduleService.createModule(newDefaultModule());
        SmartService service = smartServiceService.addServiceToModule(module.getId(), EMBEDDED_SMART_SERVICE_ID);
        mockMvc.perform(delete(String.format("/module/%d", module.getId())))
                .andExpect(status().isNoContent());
        assertThat(moduleRepository.count()).isZero();
        assertThat(smartServiceRepository.findAll()).extracting(SmartService::getModule).extracting(Module::getId).doesNotContain(service.getId());
    }


    private static Environment newDefaultEnvironment() {
        return new DefaultEnvironment.Builder()
                .assetAdministrationShells(new DefaultAssetAdministrationShell.Builder()
                        .id("http://example.org/aas/1")
                        .submodels(ReferenceBuilder.forSubmodel("http://example.org/aas/1"))
                        .build())
                .submodels(new DefaultSubmodel.Builder()
                        .id("http://example.org/aas/1")
                        .id("submodel1")
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
}
