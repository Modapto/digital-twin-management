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
package eu.modapto.digitaltwinmanagement.repository;

import eu.modapto.digitaltwinmanagement.model.Module;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class LiveModuleRepository {
    private final ModuleRepository moduleRepository;
    private final Map<String, Module> modules;

    @Autowired
    public LiveModuleRepository(ModuleRepository moduleRepository) {
        this.moduleRepository = moduleRepository;
        modules = Collections.synchronizedMap(new HashMap<>());
    }


    @PostConstruct
    public void init() {
        modules.putAll(moduleRepository.findAll().stream().collect(Collectors.toMap(Module::getId, x -> x)));
    }


    public boolean contains(String id) {
        return modules.containsKey(id);
    }


    public Module get(String id) {
        return modules.get(id);
    }


    public List<Module> getAll() {
        return new ArrayList<>(modules.values());
    }


    public void subscribe(Module module) {
        modules.put(module.getId(), module);
    }


    public void unsubscribe(Module module) {
        modules.remove(module.getId());
    }
}
