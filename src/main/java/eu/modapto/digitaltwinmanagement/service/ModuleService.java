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

import eu.modapto.digitaltwinmanagement.deployment.DigitalTwinManager;
import eu.modapto.digitaltwinmanagement.exception.ResourceNotFoundException;
import eu.modapto.digitaltwinmanagement.model.Module;
import eu.modapto.digitaltwinmanagement.repository.ModuleRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class ModuleService {
    @Autowired
    private DigitalTwinManager dtManager;

    @Autowired
    private ModuleRepository moduleRepository;

    public Module createModule(Module module) throws Exception {
        Module result = moduleRepository.save(module);
        dtManager.deploy(module);
        return moduleRepository.save(result);
    }


    public List<Module> getAllModules() {
        return moduleRepository.findAll();
    }


    public Module updateModule(Long moduleId, Module module) {
        Module existingModule = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

        return moduleRepository.save(existingModule);
    }


    public void deleteModule(Long moduleId) throws Exception {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));
        dtManager.undeploy(module);
        module.getServices().forEach(x -> {
            x.setModule(null);
        });
        moduleRepository.delete(module);
    }


    public Module getModuleById(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));
    }
}
