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
package eu.modapto.digitaltwinmanagement.util;

import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.util.DeepCopyHelper;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;


public class Helper {
    private Helper() {

    }


    public static EnvironmentContext deepCopy(EnvironmentContext environmentContext) {
        return EnvironmentContext.builder()
                .environment(DeepCopyHelper.deepCopy(environmentContext.getEnvironment()))
                .files(environmentContext.getFiles().stream()
                        .map(x -> new InMemoryFile(
                                Arrays.copyOf(x.getFileContent(), x.getFileContent().length),
                                x.getPath()))
                        .collect(Collectors.toList()))
                .build();
    }
}
