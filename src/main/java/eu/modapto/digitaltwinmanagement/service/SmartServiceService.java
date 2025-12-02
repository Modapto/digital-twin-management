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
package eu.modapto.digitaltwinmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.config.DigitalTwinManagementConfig;
import eu.modapto.digitaltwinmanagement.deployment.DigitalTwinManager;
import eu.modapto.digitaltwinmanagement.exception.ResourceNotFoundException;
import eu.modapto.digitaltwinmanagement.messagebus.KafkaBridge;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceAssignedEvent;
import eu.modapto.digitaltwinmanagement.model.event.SmartServiceUnassignedEvent;
import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceAssignedPayload;
import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceUnassignedPayload;
import eu.modapto.digitaltwinmanagement.model.request.SmartServiceRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.external.catalog.ServiceDetailsResponseDto;
import eu.modapto.digitaltwinmanagement.repository.ModuleRepository;
import eu.modapto.digitaltwinmanagement.repository.SmartServiceRepository;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;


@Service
@Transactional
public class SmartServiceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmartServiceService.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Random random = new Random();

    private final DigitalTwinManagementConfig config;
    private final SmartServiceRepository smartServiceRepository;
    private final ModuleRepository moduleRepository;
    private final ObjectMapper mapper;
    private final DigitalTwinManager dtManager;
    private final KafkaBridge kafkaBridge;

    @Autowired
    public SmartServiceService(DigitalTwinManagementConfig config,
            SmartServiceRepository smartServiceRepository,
            ModuleRepository moduleRepository,
            ObjectMapper mapper,
            DigitalTwinManager dtManager,
            KafkaBridge kafkaBridge) {
        this.config = config;
        this.smartServiceRepository = smartServiceRepository;
        this.moduleRepository = moduleRepository;
        this.mapper = mapper;
        this.dtManager = dtManager;
        this.kafkaBridge = kafkaBridge;
    }


    public List<SmartService> getAllSmartServices() {
        return smartServiceRepository.findAll();
    }


    public SmartService getSmartServiceById(String serviceId) {
        return smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("SmartService not found"));
    }


    public SmartService updateSmartService(String serviceId, SmartService service) {
        SmartService existingService = smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        existingService.setName(service.getName());
        if (service.getModule() != null) {
            existingService.setModule(service.getModule());
        }
        return smartServiceRepository.save(existingService);
    }


    public SmartService addServiceToModule(String moduleId, SmartServiceRequestDto request, Jwt token) throws Exception {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> {
                    fireServiceAssignedFailed(moduleId, request);
                    return new ResourceNotFoundException(String.format("Module not found (id: %s)", moduleId));
                });
        try {
            LOGGER.debug("adding service to module (moduleId: {}, serviceCatalogId: {})", moduleId, request.getServiceCatalogId());
            SmartService service = getServiceDetails(request.getServiceCatalogId(), token);
            service.setProperties(request.getProperties());
            applyRequestOverrides(service, request);
            ensureValidServicename(service);
            service.setModule(module);
            smartServiceRepository.save(service);
            module.getServices().add(service);
            dtManager.update(module);
            moduleRepository.save(module);
            smartServiceRepository.save(service);
            fireServiceAssignedSuccess(service);
            return module.getServiceById(service.getId());
        }
        catch (Exception e) {
            fireServiceAssignedFailed(moduleId, request);
            throw e;
        }

    }


    public void deleteService(String serviceId) throws Exception {
        deleteService(smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Service not found (id: %s)", serviceId))));

    }


    public void deleteServiceFromModule(String moduleId, String serviceId) throws Exception {
        SmartService service = smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Service not found (id: %s)", serviceId)));

        if (!service.getModule().getId().equals(moduleId)) {
            throw new ResourceNotFoundException("Service does not belong to the specified module");
        }
        deleteService(service);
    }


    private void deleteService(SmartService service) throws Exception {
        try {
            service.getModule().getServices().removeIf(x -> Objects.equals(x.getId(), service.getId()));
            dtManager.update(service.getModule());
            moduleRepository.save(service.getModule());
            smartServiceRepository.delete(service);
            fireServiceUnassignedEvent(service, true);
        }
        catch (Exception e) {
            fireServiceUnassignedEvent(service, false);
            throw e;
        }
    }


    private SmartService getServiceDetails(String serviceCatalogId, Jwt token) {
        LOGGER.debug("fetching service details from servie catalog (serviceCatalogId: {}, serviceCatalogHost: {}, serviceCatalogPath: {})",
                serviceCatalogId, config.getServiceCatalogueHost(), config.getServiceCataloguePath());
        String url = String.format(config.getServiceCataloguePath(), serviceCatalogId);
        SmartService result = RestClient.create(config.getServiceCatalogueHost())
                .get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token.getTokenValue())
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                        return mapper.readValue(response.getBody(), ServiceDetailsResponseDto.class).asSmartService();
                    }
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            String.format(
                                    "Bad Gateway: Service Catalog is unavailable (url: %s, response code: %s, response body: %s)",
                                    url,
                                    response.getStatusCode(),
                                    new String(response.getBody().readAllBytes())));
                });
        result.setServiceCatalogId(serviceCatalogId);
        return result;
    }


    private void fireServiceAssignedFailed(String moduleId, SmartServiceRequestDto service) {
        kafkaBridge.publish(SmartServiceAssignedEvent.builder()
                .moduleId(moduleId)
                .payload(SmartServiceAssignedPayload.builder()
                        .name(service.getName())
                        .serviceCatalogId(service.getServiceCatalogId())
                        .success(false)
                        .build())
                .build());
    }


    private void fireServiceAssignedSuccess(SmartService service) {
        kafkaBridge.publish(SmartServiceAssignedEvent.builder()
                .moduleId(service.getModule().getId())
                .payload(SmartServiceAssignedPayload.builder()
                        .serviceId(service.getId())
                        .name(service.getName())
                        .serviceCatalogId(service.getServiceCatalogId())
                        .endpoint(service.getExternalEndpoint())
                        .success(true)
                        .build())
                .build());
    }


    private void fireServiceUnassignedEvent(SmartService service, boolean success) {
        kafkaBridge.publish(SmartServiceUnassignedEvent.builder()
                .moduleId(service.getModule().getId())
                .payload(SmartServiceUnassignedPayload.builder()
                        .serviceId(service.getId())
                        .name(service.getName())
                        .serviceCatalogId(service.getServiceCatalogId())
                        .endpoint(service.getExternalEndpoint())
                        .success(success)
                        .build())
                .build());
    }


    private void ensureValidServicename(SmartService service) {
        String name = service.getName();
        String defaultName = service.getId();
        if (StringHelper.isBlank(name)) {
            name = service.getId();
        }
        else {
            name = name.replace(" ", "_")
                    .replaceAll("[^a-zA-Z0-9_]", "");
            if (!name.matches("^[a-zA-Z].*") || name.length() < 5) {
                name = service.getId();
            }
            else if (name.length() > 128) {
                name = name.substring(0, 128);
            }
        }
        service.setName(name);
    }


    private static void applyRequestOverrides(SmartService service, SmartServiceRequestDto request) {
        if (Objects.nonNull(request.getName())) {
            service.setName(request.getName());
        }
        else {
            service.setName(service.getName());
        }
        if (Objects.nonNull(request.getDescription())) {
            service.setDescription(request.getDescription());
        }
        if (Objects.nonNull(request.getInputParameters())) {
            service.setInputParameters(request.getInputParameters());
        }
        if (Objects.nonNull(request.getOutputParameters())) {
            service.setOutputParameters(request.getOutputParameters());
        }
        if (Objects.nonNull(request.getInputArgumentTypes())) {
            service.setInputArgumentTypes(request.getInputArgumentTypes());
        }
        if (Objects.nonNull(request.getOutputArgumentTypes())) {
            service.setOutputArgumentTypes(request.getOutputArgumentTypes());
        }
    }


    private static char randomLowercaseChar() {
        return (char) (random.nextInt(26) + 'a');
    }

}
