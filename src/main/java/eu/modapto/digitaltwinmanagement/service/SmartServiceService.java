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
import eu.modapto.digitaltwinmanagement.deployment.DigitalTwinManager;
import eu.modapto.digitaltwinmanagement.exception.ResourceNotFoundException;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.model.request.SmartServiceRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.external.catalog.ServiceDetailsResponseDto;
import eu.modapto.digitaltwinmanagement.repository.ModuleRepository;
import eu.modapto.digitaltwinmanagement.repository.SmartServiceRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;


@Service
@Transactional
public class SmartServiceService {

    @Value("${modapto.service-catalogue.url}")
    private String serviceCatalogueEndpoint;

    @Autowired
    private SmartServiceRepository smartServiceRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DigitalTwinManager dtManager;

    public List<SmartService> getAllSmartServices() {
        return smartServiceRepository.findAll();
    }


    public SmartService getSmartServiceById(Long serviceId) {
        return smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("SmartService not found"));
    }


    public SmartService updateSmartService(Long serviceId, SmartService service) {
        SmartService existingService = smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        existingService.setName(service.getName());
        if (service.getModule() != null) {
            existingService.setModule(service.getModule());
        }
        return smartServiceRepository.save(existingService);
    }


    public SmartService addServiceToModule(Long moduleId, SmartServiceRequestDto request) throws Exception {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Module not found (id: %s)", moduleId)));
        SmartService service = getServiceDetails(request.getServiceId());
        applyRequestOverrides(service, request);
        service.setModule(module);
        smartServiceRepository.save(service);
        module.getServices().add(service);
        dtManager.update(module);
        moduleRepository.save(module);
        return module.getServiceById(service.getId());
    }


    private static void applyRequestOverrides(SmartService service, SmartServiceRequestDto request) {
        if (Objects.nonNull(request.getDescription())) {
            service.setName(request.getName());
        }
        else {
            service.setName(String.format("%s_%s",
                    service.getName(),
                    UUID.randomUUID().toString().replace("-", "").substring(0, 16)));
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


    private SmartService getServiceDetails(String serviceId) {
        SmartService result = RestClient.create(serviceCatalogueEndpoint)
                .get()
                .uri("/service/{serviceId}", serviceId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                        return mapper.readValue(response.getBody(), ServiceDetailsResponseDto.class).asSmartService();
                    }
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            String.format("Bad Gateway: Service Catalog is unavailable (endpoint: %s", serviceCatalogueEndpoint));
                });
        result.setServiceId(serviceId);
        return result;
    }


    public void deleteService(Long serviceId) throws Exception {
        deleteService(smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Service not found (id: %s)", serviceId))));

    }


    private void deleteService(SmartService service) throws Exception {
        service.getModule().getServices().removeIf(x -> Objects.equals(x.getId(), service.getId()));
        dtManager.update(service.getModule());
        moduleRepository.save(service.getModule());
        smartServiceRepository.delete(service);
    }


    public void deleteServiceFromModule(Long moduleId, Long serviceId) throws Exception {
        SmartService service = smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Service not found (id: %s)", serviceId)));

        if (!service.getModule().getId().equals(moduleId)) {
            throw new ResourceNotFoundException("Service does not belong to the specified module");
        }
        deleteService(service);
    }
}
