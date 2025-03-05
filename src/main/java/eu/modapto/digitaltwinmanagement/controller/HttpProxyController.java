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

import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.repository.LiveModuleRepository;
import eu.modapto.digitaltwinmanagement.util.AddressTranslationHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;


@RestController
@RequestMapping("/digital-twins")
@ConditionalOnProperty(name = "dt-management.useProxy", havingValue = "true")
@Tag(name = "Digital Twin HTTP Proxy", description = "Acts as a proxy for Digital Twins. Only available if 'dt-management.useProxy' is set to 'true' in configuration.")
public class HttpProxyController {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyController.class);
    private final LiveModuleRepository liveModuleRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public HttpProxyController(LiveModuleRepository liveModuleRepository) {
        this.liveModuleRepository = liveModuleRepository;
        this.restTemplate = new RestTemplate();
    }


    @RequestMapping(value = "/{moduleId:[0-9a-fA-F-]{36}}/**", method = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.DELETE,
            RequestMethod.HEAD
    })
    @Operation(summary = "Forward call to Digital Twin", description = "Forwards call to Digital Twin based on moduleId keeping the URL path after /{moduleId}")
    public ResponseEntity<?> proxy(@PathVariable("moduleId") String moduleId, HttpMethod method, HttpServletRequest request) {
        if (!liveModuleRepository.contains(moduleId)) {
            return ResponseEntity.notFound().build();
        }
        Module module = liveModuleRepository.get(moduleId);
        String remainingPath = request.getRequestURI().substring(request.getRequestURI().indexOf(moduleId) + moduleId.length());
        String url = AddressTranslationHelper.getHostToModuleAddress(module, module.getExternalPort()).asUrl() + remainingPath;
        try {
            LOGGER.trace("executing proxy call to DT (url: {})", url);
            ResponseEntity<byte[]> actualRespose = restTemplate.exchange(url, method, copyBodyAndHeaders(request), byte[].class);
            try {
                LOGGER.debug("received response from DT (url: {}, status code: {}, payload: {})",
                        url,
                        actualRespose.getStatusCode(),
                        new String(actualRespose.getBody()));
            }
            catch (Exception e) {
                LOGGER.debug("failed to log response", e);
            }
            return ResponseEntity
                    .status(actualRespose.getStatusCode())
                    .contentType(actualRespose.getHeaders().getContentType())
                    .contentLength(actualRespose.getHeaders().getContentLength())
                    .location(actualRespose.getHeaders().getLocation())
                    .body(actualRespose.getBody());
        }
        catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsByteArray());
        }
        catch (IOException e) {
            LOGGER.debug("error proxying HTTP call to Digital Twin (moduleId: {}, reason: {})", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(String.format("Failed to read payload (reason: %s)", e.getMessage()));
        }
        catch (Exception e) {
            LOGGER.debug("unkown error proxying HTTP call to Digital Twin (moduleId: {}, reason: {})", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(String.format("unkown error proxying HTTP call to Digital Twin (moduleId: {}, reason: {})", e.getMessage()));
        }
    }


    private static HttpEntity<byte[]> copyBodyAndHeaders(HttpServletRequest request) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> headers.add(headerName, request.getHeader(headerName)));
        return new HttpEntity<>(request.getInputStream().readAllBytes(), headers);
    }
}
