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

import de.fraunhofer.iosb.ilt.faaast.service.util.StreamHelper;
import eu.modapto.digitaltwinmanagement.config.SecurityConfig;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.repository.LiveModuleRepository;
import eu.modapto.digitaltwinmanagement.util.AddressTranslationHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/digital-twins")
@ConditionalOnProperty(name = "dt-management.useProxy", havingValue = "true")
@Tag(name = "Digital Twin HTTP Proxy", description = "Acts as a proxy for Digital Twins. Only available if 'dt-management.useProxy' is set to 'true' in configuration.")
public class HttpProxyController {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyController.class);
    private static final List<String> NON_FORWARDABLE_HEADERS = List.of(HttpHeaders.AUTHORIZATION);
    private final LiveModuleRepository liveModuleRepository;
    private final SecurityConfig securityConfig;

    @Autowired
    public HttpProxyController(LiveModuleRepository liveModuleRepository, SecurityConfig securityConfig) {
        this.liveModuleRepository = liveModuleRepository;
        this.securityConfig = securityConfig;
    }


    @RequestMapping(value = "/{moduleId:[0-9a-fA-F-]{36}}/**", method = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.DELETE,
            RequestMethod.HEAD
    })
    @Operation(summary = "Forward call to Digital Twin", description = "Forwards call to Digital Twin based on moduleId keeping the URL path after /{moduleId}. Security is required by default but might be disabled via config property 'dt-management.security.secureProxyDTs=false'.", security = {
            @SecurityRequirement(name = "none"),
            @SecurityRequirement(name = "bearerToken")
    })
    public ResponseEntity<?> proxy(@PathVariable("moduleId") String moduleId, HttpMethod method, HttpServletRequest request) {
        if (!liveModuleRepository.contains(moduleId)) {
            return ResponseEntity.notFound().build();
        }
        Module module = liveModuleRepository.get(moduleId);
        String remainingPath = request.getRequestURI().substring(request.getRequestURI().indexOf(moduleId) + moduleId.length());
        String url = AddressTranslationHelper.getHostToModuleAddress(module, module.getExternalPort()).asUrl() + remainingPath;
        try {
            byte[] body = request.getInputStream().readAllBytes();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder proxyRequestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(url));
            copyHeaders(request, proxyRequestBuilder);
            if (Objects.nonNull(body) && body.length > 0) {
                proxyRequestBuilder.method(method.toString(), HttpRequest.BodyPublishers.ofByteArray(body));
            }
            else {
                proxyRequestBuilder.method(method.toString(), HttpRequest.BodyPublishers.noBody());
            }
            HttpRequest proxyRequest = proxyRequestBuilder.build();
            LOGGER.debug("executing proxy call to DT (url: {}, method: {}, headers: {}, body: {})",
                    proxyRequest.uri(),
                    proxyRequest.method(),
                    proxyRequest.headers(),
                    Objects.nonNull(body) ? new String(body) : "[empty]");
            HttpResponse<byte[]> actualRespose = client.send(proxyRequestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            try {
                LOGGER.debug("received response from DT (url: {}, status code: {}, payload: {})",
                        url,
                        actualRespose.statusCode(),
                        new String(actualRespose.body()));
            }
            catch (Exception e) {
                LOGGER.debug("failed to log response", e);
            }
            var response = ResponseEntity
                    .status(actualRespose.statusCode());

            Optional<String> contentType = actualRespose.headers().firstValue(HttpHeaders.CONTENT_TYPE);
            if (contentType.isPresent()) {
                response.contentType(MediaType.parseMediaType(contentType.get()));
            }
            OptionalLong contentLength = actualRespose.headers().firstValueAsLong(HttpHeaders.CONTENT_LENGTH);
            if (contentLength.isPresent()) {
                response.contentLength(contentLength.getAsLong());
            }
            Optional<String> location = actualRespose.headers().firstValue(HttpHeaders.LOCATION);
            if (location.isPresent()) {
                response.location(new URI(location.get()));
            }
            return response.body(actualRespose.body());
        }
        catch (IOException e) {
            LOGGER.debug("error proxying HTTP call to Digital Twin (moduleId: {}, reason: {})", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(String.format("Failed to read payload (reason: %s)", e.getMessage()));
        }
        catch (Exception e) {
            LOGGER.debug("unkown error proxying HTTP call to Digital Twin (moduleId: {}, reason: {})", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(String.format("unkown error proxying HTTP call to Digital Twin (moduleId: %s, reason: %s)", moduleId, e.getMessage()));
        }
    }


    private void copyHeaders(HttpServletRequest request, HttpRequest.Builder proxyRequestBuilder) {
        StreamHelper.toStream(request.getHeaderNames())
                .filter(x -> !securityConfig.isSecureProxyDTs() || NON_FORWARDABLE_HEADERS.stream().noneMatch(y -> y.equalsIgnoreCase(x)))
                .forEach(key -> StreamHelper.toStream(request.getHeaders(key)).forEach(value -> {
                    try {
                        proxyRequestBuilder.header(key, value);
                    }
                    catch (IllegalArgumentException e) {
                        // ignore, as this filters out any restricted headers that are not allowed to be forwarded
                    }
                }));
    }
}
