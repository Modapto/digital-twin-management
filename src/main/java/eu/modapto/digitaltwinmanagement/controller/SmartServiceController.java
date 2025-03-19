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
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
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

    private final ModuleService moduleService;
    private final SmartServiceService smartServiceService;

    public SmartServiceController(ModuleService moduleService, SmartServiceService smartServiceService) {
        this.moduleService = moduleService;
        this.smartServiceService = smartServiceService;
    }


    @Operation(summary = "Get all smart services", description = "Returns a list of all smart services", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping("/services")
    public List<SmartServiceResponseDto> getAllSmartServices() {
        return smartServiceService.getAllSmartServices().stream()
                .map(SmartServiceMapper::toDto)
                .toList();
    }


    @Operation(summary = "Get smart service by ID", description = "Returns the details of an existing smart service by its ID", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Smart Service not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping("/services/{serviceId}")
    public SmartServiceResponseDto getSmartService(@PathVariable String serviceId) {
        return SmartServiceMapper.toDto(smartServiceService.getSmartServiceById(serviceId));
    }


    @Operation(summary = "Create a new smart service", description = "Creates a new smart service withing a service based on the provided details", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Smart Service created successfully", headers = {
                    @Header(name = HttpHeaders.LOCATION, description = "URI of the created Smart Service", required = true)
            }),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Module not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @PostMapping("/modules/{moduleId}/services")
    public ResponseEntity<SmartServiceResponseDto> createService(@PathVariable String moduleId, @RequestBody SmartServiceRequestDto request) throws Exception {
        SmartServiceResponseDto result = SmartServiceMapper.toDto(smartServiceService.addServiceToModule(moduleId, request));
        return ResponseEntity
                .created(URI.create("/services/" + result.getId()))
                .body(result);
    }


    @Operation(summary = "Get services for a module", description = "Returns a list of services associated with the specified module", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Module not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping("/modules/{moduleId}/services")
    public List<SmartServiceResponseDto> getServicesForModule(@PathVariable String moduleId) {
        return moduleService.getModuleById(moduleId).getServices().stream()
                .map(SmartServiceMapper::toDto)
                .toList();
    }


    @Operation(summary = "Delete a service from a module", description = "Deletes a service by its ID from the specified module", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Smart Service deleted from module successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Module or Smart Service not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/modules/{moduleId}/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteServiceFromModule(@PathVariable String moduleId, @PathVariable String serviceId) throws Exception {
        smartServiceService.deleteServiceFromModule(moduleId, serviceId);
    }


    @Operation(summary = "Delete a smart service", description = "Deletes a smart service by its ID", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Smart Service deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Smart Service not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSmartService(@PathVariable String serviceId) throws Exception {
        smartServiceService.deleteService(serviceId);
    }
}
