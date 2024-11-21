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

import eu.modapto.digitaltwinmanagement.exception.ResourceNotFoundException;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.SmartService;
import eu.modapto.digitaltwinmanagement.repository.ModuleRepository;
import eu.modapto.digitaltwinmanagement.repository.SmartServiceRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class SmartServiceService {

    @Autowired
    private SmartServiceRepository smartServiceRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    public List<SmartService> getAllSmartServices() {
        return smartServiceRepository.findAll();
    }


    public SmartService getSmartServiceById(Long serviceId) {
        return smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("SmartService not found"));
    }


    public SmartService createSmartService(SmartService service) {
        return smartServiceRepository.save(service);
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


    public void deleteSmartService(Long serviceId) {
        smartServiceRepository.deleteById(serviceId);
    }


    public SmartService addServiceToModule(Long moduleId, SmartService service) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

        service.setModule(module);
        return smartServiceRepository.save(service);
    }


    public void deleteServiceFromModule(Long moduleId, Long serviceId) {
        SmartService service = smartServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (!service.getModule().getId().equals(moduleId)) {
            throw new ResourceNotFoundException("Service does not belong to the specified module");
        }

        smartServiceRepository.delete(service);
    }
}
