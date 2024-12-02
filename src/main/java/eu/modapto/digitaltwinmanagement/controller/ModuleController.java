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

import de.fraunhofer.iosb.ilt.faaast.service.dataformat.DeserializationException;
import eu.modapto.digitaltwinmanagement.mapper.ModuleMapper;
import eu.modapto.digitaltwinmanagement.mapper.SmartServiceMapper;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.request.ModuleRequestDto;
import eu.modapto.digitaltwinmanagement.model.request.SmartServiceRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.ModuleResponseDto;
import eu.modapto.digitaltwinmanagement.model.response.SmartServiceResponseDto;
import eu.modapto.digitaltwinmanagement.service.ModuleService;
import eu.modapto.digitaltwinmanagement.service.SmartServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/module")
public class ModuleController {

    @Autowired
    private ModuleService moduleService;

    @Autowired
    private SmartServiceService smartServiceService;

    @Operation(summary = "Create a new module", description = "Creates a new module based on the provided details")
    @ApiResponse(responseCode = "201", description = "Module created successfully")
    @PostMapping
    public ResponseEntity<ModuleResponseDto> createModule(@RequestBody ModuleRequestDto moduleRequestDto) throws Exception {
        Module module = ModuleMapper.toEntity(moduleRequestDto);
        module = moduleService.createModule(module);
        ModuleResponseDto result = ModuleMapper.toDto(module);
        return ResponseEntity
                .created(URI.create("/module/" + result.getId()))
                .body(result);
    }


    @Operation(summary = "Get all modules", description = "Returns a list of all modules")
    @GetMapping
    public List<ModuleResponseDto> getAllModules() {
        return moduleService.getAllModules().stream()
                .map(ModuleMapper::toDto)
                .toList();
    }


    @Operation(summary = "Get module by ID", description = "Returns the details of an existing module by its ID")
    @GetMapping("/{moduleId}")
    public ModuleResponseDto getModule(@PathVariable Long moduleId) {
        return ModuleMapper.toDto(moduleService.getModuleById(moduleId));
    }


    @Operation(summary = "Update an existing module", description = "Updates the details of an existing module")
    @ApiResponse(responseCode = "200", description = "Module updated successfully")
    @PutMapping("/{moduleId}")
    public ModuleResponseDto updateModule(@PathVariable Long moduleId, @RequestBody ModuleRequestDto module) throws DeserializationException {
        return ModuleMapper.toDto(moduleService.updateModule(moduleId, ModuleMapper.toEntity(module)));
    }


    @Operation(summary = "Delete a module", description = "Deletes a module by its ID")
    @ApiResponse(responseCode = "204", description = "Module deleted successfully")
    @DeleteMapping("/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModule(@PathVariable Long moduleId) throws Exception {
        moduleService.deleteModule(moduleId);
    }


    @Operation(summary = "Create a new smart service", description = "Creates a new smart service withing a service based on the provided details")
    @ApiResponse(responseCode = "200", description = "Smart service created successfully")
    @PostMapping("/{moduleId}/service")
    public ResponseEntity<SmartServiceResponseDto> createService(@PathVariable Long moduleId, @RequestBody SmartServiceRequestDto service) throws Exception {
        SmartServiceResponseDto result = SmartServiceMapper.toDto(smartServiceService.addServiceToModule(moduleId, service.getServiceId()));
        return ResponseEntity
                .created(URI.create("/service/" + result.getId()))
                .body(result);
    }


    @Operation(summary = "Get services for a module", description = "Returns a list of services associated with the specified module")
    @GetMapping("/{moduleId}/service")
    public List<SmartServiceResponseDto> getServicesForModule(@PathVariable Long moduleId) {
        return moduleService.getModuleById(moduleId).getServices().stream()
                .map(SmartServiceMapper::toDto)
                .toList();
    }


    @Operation(summary = "Delete a service from a module", description = "Deletes a service by its ID from the specified module")
    @ApiResponse(responseCode = "204", description = "Service deleted from module successfully")
    @DeleteMapping("/{moduleId}/service/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteServiceFromModule(@PathVariable Long moduleId, @PathVariable Long serviceId) throws Exception {
        smartServiceService.deleteServiceFromModule(moduleId, serviceId);
    }
}
