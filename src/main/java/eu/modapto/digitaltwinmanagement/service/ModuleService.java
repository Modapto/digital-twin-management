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

import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.deployment.DigitalTwinManager;
import eu.modapto.digitaltwinmanagement.exception.BadRequestException;
import eu.modapto.digitaltwinmanagement.exception.ResourceNotFoundException;
import eu.modapto.digitaltwinmanagement.messagebus.KafkaBridge;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.model.event.ModuleCreatedEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleDeletedEvent;
import eu.modapto.digitaltwinmanagement.model.event.ModuleUpdatedEvent;
import eu.modapto.digitaltwinmanagement.model.event.payload.ModuleDetailsPayload;
import eu.modapto.digitaltwinmanagement.repository.ModuleRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class ModuleService {

    private static final String ERROR_MSG_MODULE_NOT_FOUND = "module not found";
    private final DigitalTwinManager dtManager;
    private final ModuleRepository moduleRepository;
    private final KafkaBridge kafkaBridge;

    @Autowired
    public ModuleService(DigitalTwinManager dtManager, ModuleRepository moduleRepository, KafkaBridge kafkaBridge) {
        this.dtManager = dtManager;
        this.moduleRepository = moduleRepository;
        this.kafkaBridge = kafkaBridge;
    }


    private void setModuleNameIfNotPresent(Module module) {
        try {
            if (StringHelper.isBlank(module.getName())) {
                module.setName(module.getProvidedModel().getEnvironment().getAssetAdministrationShells().get(0).getIdShort());
            }
        }
        catch (Exception e) {
            throw new BadRequestException("Module name not provided and could not be autmatically resolved");
        }
    }


    public Module createModule(Module module) throws Exception {
        setModuleNameIfNotPresent(module);
        Module result = moduleRepository.save(module);
        dtManager.deploy(module);
        kafkaBridge.publish(ModuleCreatedEvent.builder()
                .payload(ModuleDetailsPayload.builder()
                        .moduleId(module.getId())
                        .name(module.getName())
                        .endpoint(module.getExternalEndpoint())
                        .build())
                .build());
        return moduleRepository.save(result);
    }


    public List<Module> getAllModules() {
        return moduleRepository.findAll();
    }


    public Module updateModule(String moduleId, Module newModule) throws Exception {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(ERROR_MSG_MODULE_NOT_FOUND));
        module.setProvidedModel(newModule.getProvidedModel());
        if (!StringHelper.isBlank(newModule.getName())) {
            module.setName(newModule.getName());
        }
        module.setType(newModule.getType());
        module.setAssetConnections(newModule.getAssetConnections());
        dtManager.update(module);
        kafkaBridge.publish(ModuleUpdatedEvent.builder()
                .payload(ModuleDetailsPayload.builder()
                        .moduleId(module.getId())
                        .name(newModule.getName())
                        .endpoint(module.getExternalEndpoint())
                        .build())
                .build());
        return moduleRepository.save(module);
    }


    public void deleteModule(String moduleId) throws Exception {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(ERROR_MSG_MODULE_NOT_FOUND));
        dtManager.undeploy(module);
        module.getServices().forEach(x -> {
            x.setModule(null);
        });
        moduleRepository.delete(module);
        kafkaBridge.publish(ModuleDeletedEvent.builder()
                .moduleId(moduleId)
                .build());
    }


    public Module getModuleById(String moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(ERROR_MSG_MODULE_NOT_FOUND));
    }
}
