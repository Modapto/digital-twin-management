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

import de.fraunhofer.iosb.ilt.faaast.service.dataformat.SerializationException;
import eu.modapto.digitaltwinmanagement.mapper.ModuleMapper;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.request.ModuleRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.ModuleDetailsResponseDto;
import eu.modapto.digitaltwinmanagement.model.response.ModuleResponseDto;
import eu.modapto.digitaltwinmanagement.service.ModuleService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/module")
@Tag(name = "Module Operations", description = "Operations related to module management")
public class ModuleController {

    @Autowired
    private ModuleService moduleService;

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


    @Operation(summary = "Get module by ID", description = "Returns an existing module by its ID")
    @GetMapping("/{moduleId}")
    public ModuleResponseDto getModule(@PathVariable Long moduleId) {
        return ModuleMapper.toDto(moduleService.getModuleById(moduleId));
    }


    @Operation(summary = "Get module details by ID", description = "Returns the details of an existing module by its ID")
    @GetMapping("/{moduleId}/details")
    public ModuleDetailsResponseDto getModuleDetails(@PathVariable Long moduleId) throws SerializationException {
        return ModuleMapper.toDetailsDto(moduleService.getModuleById(moduleId));
    }


    @Operation(summary = "Update an existing module", description = "Updates the details of an existing module")
    @ApiResponse(responseCode = "200", description = "Module updated successfully")
    @PutMapping("/{moduleId}")
    public ModuleResponseDto updateModule(@PathVariable Long moduleId, @RequestBody ModuleRequestDto module) throws Exception {
        return ModuleMapper.toDto(moduleService.updateModule(moduleId, ModuleMapper.toEntity(module)));
    }


    @Operation(summary = "Delete a module", description = "Deletes a module by its ID")
    @ApiResponse(responseCode = "204", description = "Module deleted successfully")
    @DeleteMapping("/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModule(@PathVariable Long moduleId) throws Exception {
        moduleService.deleteModule(moduleId);
    }

}
