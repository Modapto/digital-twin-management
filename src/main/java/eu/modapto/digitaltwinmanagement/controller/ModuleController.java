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

import de.fraunhofer.iosb.ilt.faaast.service.dataformat.EnvironmentSerializationManager;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.SerializationException;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.model.exception.ValidationException;
import de.fraunhofer.iosb.ilt.faaast.service.model.validation.ModelValidator;
import de.fraunhofer.iosb.ilt.faaast.service.model.validation.ModelValidatorConfig;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.exception.InvalidModelException;
import eu.modapto.digitaltwinmanagement.mapper.ModuleMapper;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.request.ModuleRequestDto;
import eu.modapto.digitaltwinmanagement.model.response.ModuleDetailsResponseDto;
import eu.modapto.digitaltwinmanagement.model.response.ModuleResponseDto;
import eu.modapto.digitaltwinmanagement.service.ModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/modules")
@Tag(name = "Module Operations", description = "Operations related to module management")
public class ModuleController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleController.class);
    private final ModuleService moduleService;

    @Autowired
    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }


    @Operation(summary = "Create a new module", description = "Creates a new module based on the provided details", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Module created successfully", headers = {
                    @Header(name = HttpHeaders.LOCATION, description = "URI of the created Module", required = true)
            }),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ModuleResponseDto> createModule(@RequestBody ModuleRequestDto moduleRequestDto) throws Exception {
        validate(moduleRequestDto);
        Module module = ModuleMapper.toEntity(moduleRequestDto);
        module = moduleService.createModule(module);
        ModuleResponseDto result = ModuleMapper.toDto(module);
        return ResponseEntity
                .created(URI.create("/modules/" + result.getId()))
                .body(result);
    }


    @Operation(summary = "Get all modules", description = "Returns a list of all modules", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping
    public List<ModuleResponseDto> getAllModules() {
        return moduleService.getAllModules().stream()
                .map(ModuleMapper::toDto)
                .toList();
    }


    @Operation(summary = "Get module by ID", description = "Returns an existing module by its ID", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Module not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping("/{moduleId}")
    public ModuleResponseDto getModule(@PathVariable String moduleId) {
        return ModuleMapper.toDto(moduleService.getModuleById(moduleId));
    }


    @Operation(summary = "Get module details by ID", description = "Returns the details of an existing module by its ID", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Module not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping("/{moduleId}/details")
    public ModuleDetailsResponseDto getModuleDetails(@PathVariable String moduleId) throws SerializationException {
        return ModuleMapper.toDetailsDto(moduleService.getModuleById(moduleId));
    }


    @Operation(summary = "Update an existing module", description = "Updates the details of an existing module", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module updated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Module not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @PutMapping("/{moduleId}")
    public ModuleResponseDto updateModule(@PathVariable String moduleId, @RequestBody ModuleRequestDto moduleRequestDto) throws Exception {
        validate(moduleRequestDto);
        return ModuleMapper.toDto(moduleService.updateModule(moduleId, ModuleMapper.toEntity(moduleRequestDto)));
    }


    @Operation(summary = "Delete a module", description = "Deletes a module by its ID", security = @SecurityRequirement(name = "bearerToken"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Module deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Module not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModule(@PathVariable String moduleId) throws Exception {
        moduleService.deleteModule(moduleId);
    }


    private static void validate(ModuleRequestDto moduleRequestDto) throws InvalidModelException {
        if (Objects.isNull(moduleRequestDto)) {
            throw new InvalidModelException("Request must be non-empty");
        }
        if (StringHelper.isBlank(moduleRequestDto.getAas())) {
            throw new InvalidModelException("Property 'aas' must be non-empty");
        }

        EnvironmentContext environmentContext;
        try {
            environmentContext = EnvironmentSerializationManager
                    .deserializerFor(moduleRequestDto.getFormat())
                    .read(new ByteArrayInputStream(Base64.getDecoder().decode(moduleRequestDto.getAas())));
        }
        catch (Exception e) {
            throw new InvalidModelException(String.format("Invalid AAS model - could not be deserialized (reason: %s)", e.getMessage()), e);
        }
        if (Objects.isNull(environmentContext)) {
            throw new InvalidModelException("Model must be non-null");
        }
        Environment environment = environmentContext.getEnvironment();
        if (Objects.isNull(environment)) {
            throw new InvalidModelException("Environment must be non-null");
        }
        if (Objects.isNull(environment.getAssetAdministrationShells()) || environment.getAssetAdministrationShells().size() != 1) {
            throw new InvalidModelException("Model must contain exactly one Asset Administration Shell");
        }
        try {
            ModelValidator.validate(environment, ModelValidatorConfig.ALL);
        }
        catch (ValidationException e) {
            throw new InvalidModelException(String.format("Model failed to validate (reason: %s)", e.getMessage()), e);
        }
        if (StringHelper.isBlank(moduleRequestDto.getName())) {
            moduleRequestDto.setName(environment.getAssetAdministrationShells().get(0).getIdShort());
        }
    }
}
