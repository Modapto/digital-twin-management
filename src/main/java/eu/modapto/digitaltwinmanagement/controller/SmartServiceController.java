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
import eu.modapto.digitaltwinmanagement.model.response.SmartServiceResponseDto;
import eu.modapto.digitaltwinmanagement.service.SmartServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/service")
public class SmartServiceController {

    @Autowired
    private SmartServiceService smartServiceService;

    @Operation(summary = "Get all smart services", description = "Returns a list of all smart services")
    @GetMapping
    public List<SmartServiceResponseDto> getAllSmartServices() {
        return smartServiceService.getAllSmartServices().stream()
                .map(SmartServiceMapper::toDto)
                .toList();
    }


    @Operation(summary = "Get smart service by ID", description = "Returns the details of an existing smart service by its ID")
    @GetMapping("/{serviceId}")
    public SmartServiceResponseDto getSmartService(@PathVariable Long serviceId) {
        return SmartServiceMapper.toDto(smartServiceService.getSmartServiceById(serviceId));
    }


    @Operation(summary = "Delete a smart service", description = "Deletes a smart service by its ID")
    @ApiResponse(responseCode = "204", description = "Smart service deleted successfully")
    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSmartService(@PathVariable Long serviceId) throws Exception {
        smartServiceService.deleteService(serviceId);
    }
}
