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

import eu.modapto.digitaltwinmanagement.mapper.SmartServiceMapper;
import eu.modapto.digitaltwinmanagement.model.request.SmartServiceRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.SmartServiceResponseDto;
import eu.modapto.digitaltwinmanagement.service.ModuleService;
import eu.modapto.digitaltwinmanagement.service.SmartServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Tag(name = "Smart Service Operations", description = "Operations related to smart service management")
public class SmartServiceController {

    @Autowired
    private ModuleService moduleService;

    @Autowired
    private SmartServiceService smartServiceService;

    @Operation(summary = "Get all smart services", description = "Returns a list of all smart services")
    @GetMapping("/services")
    public List<SmartServiceResponseDto> getAllSmartServices() {
        return smartServiceService.getAllSmartServices().stream()
                .map(SmartServiceMapper::toDto)
                .toList();
    }


    @Operation(summary = "Get smart service by ID", description = "Returns the details of an existing smart service by its ID")
    @GetMapping("/services/{serviceId}")
    public SmartServiceResponseDto getSmartService(@PathVariable Long serviceId) {
        return SmartServiceMapper.toDto(smartServiceService.getSmartServiceById(serviceId));
    }


    @Operation(summary = "Create a new smart service", description = "Creates a new smart service withing a service based on the provided details")
    @ApiResponse(responseCode = "200", description = "Smart service created successfully")
    @PostMapping("/modules/{moduleId}/services")
    public ResponseEntity<SmartServiceResponseDto> createService(@PathVariable Long moduleId, @RequestBody SmartServiceRequestDto request) throws Exception {
        SmartServiceResponseDto result = SmartServiceMapper.toDto(smartServiceService.addServiceToModule(moduleId, request));
        return ResponseEntity
                .created(URI.create("/services/" + result.getId()))
                .body(result);
    }


    @Operation(summary = "Get services for a module", description = "Returns a list of services associated with the specified module")
    @GetMapping("/modules/{moduleId}/services")
    public List<SmartServiceResponseDto> getServicesForModule(@PathVariable Long moduleId) {
        return moduleService.getModuleById(moduleId).getServices().stream()
                .map(SmartServiceMapper::toDto)
                .toList();
    }


    @Operation(summary = "Delete a service from a module", description = "Deletes a service by its ID from the specified module")
    @ApiResponse(responseCode = "204", description = "Service deleted from module successfully")
    @DeleteMapping("/modules/{moduleId}/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteServiceFromModule(@PathVariable Long moduleId, @PathVariable Long serviceId) throws Exception {
        smartServiceService.deleteServiceFromModule(moduleId, serviceId);
    }


    @Operation(summary = "Delete a smart service", description = "Deletes a smart service by its ID")
    @ApiResponse(responseCode = "204", description = "Smart service deleted successfully")
    @DeleteMapping("/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSmartService(@PathVariable Long serviceId) throws Exception {
        smartServiceService.deleteService(serviceId);
    }
}
