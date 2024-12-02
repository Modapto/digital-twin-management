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
package eu.modapto.digitaltwinmanagement.smartservice.embedded;

import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.util.EncodingHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import eu.modapto.digitaltwinmanagement.model.EmbeddedSmartService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultFile;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;


public class EmbeddedSmartServiceHelper {

    private static final Reference SEMANTIC_ID_SMT_SIMULATION = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/SimulationModels/1/0");
    private static final Reference SEMANTIC_ID_SIMULATION_MODEL = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/SimulationModel/1/0");
    private static final Reference SEMANTIC_ID_MODEL_FILE = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/ModelFile/1/0");
    private static final Reference SEMANTIC_ID_MODEL_FILE_VERSION = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/ModelFileVersion/1/0");
    private static final Reference SEMANTIC_ID_DIGITAL_FILE = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/DigitalFile/1/0");

    private static final String ID_SHORT_SIMULATION_MODELS = "SimulationModels";
    private static final String ID_SHORT_MODEL_FILE = "ModelFile";
    private static final String ID_SHORT_MODEL_FILE_VERSION = "ModelFileVersion";
    private static final String ID_SHORT_DIGITAL_FILE = "DigitalFile";

    public static void addSmartService(EnvironmentContext environmentContext, EmbeddedSmartService service) {
        Submodel submodel = getSubmodel(environmentContext);
        if (Objects.isNull(submodel)) {
            submodel = createSubmodel();
            environmentContext.getEnvironment().getSubmodels().add(submodel);
            environmentContext.getEnvironment().getAssetAdministrationShells().get(0).getSubmodels().add(ReferenceBuilder.forSubmodel(submodel));
        }
        Optional<SubmodelElement> simulationModel = submodel.getSubmodelElements().stream()
                .filter(x -> Objects.equals(x.getIdShort(), service.getName()))
                .filter(x -> ReferenceHelper.equals(x.getSemanticId(), SEMANTIC_ID_SIMULATION_MODEL))
                .findFirst();
        if (simulationModel.isPresent()) {
            throw new IllegalArgumentException(String.format("simulation model already exists (name/id: %s)", service.getName()));
        }
        submodel.getSubmodelElements().add(createSimulationModel(service));
        environmentContext.getFiles().add(new InMemoryFile(service.getFmu(), getFmuFilename(service)));
        service.setEndpoint(getSmartServiceEndpoint(service, submodel));
    }


    public static void removeSmartService(EnvironmentContext environmentContext, EmbeddedSmartService service) {
        Submodel submodel = getSubmodel(environmentContext);
        if (Objects.isNull(submodel)) {
            throw new IllegalArgumentException("No SMT Simulation found in AAS model");
        }
        Optional<SubmodelElement> simulationModel = submodel.getSubmodelElements().stream()
                .filter(x -> Objects.equals(x.getIdShort(), service.getName()))
                .filter(x -> ReferenceHelper.equals(x.getSemanticId(), SEMANTIC_ID_SIMULATION_MODEL))
                .findFirst();
        if (simulationModel.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "No simulation model found in AAS model for service (service id: %d, service name: %s)",
                    service.getId(),
                    service.getName()));
        }
        submodel.getSubmodelElements().remove(simulationModel.get());
        environmentContext.getFiles().removeIf(x -> Objects.equals(x.getPath(), getFmuFilename(service)));
        service.setEndpoint(null);
    }


    private static String getFmuFilename(EmbeddedSmartService service) {
        return "/" + service.getName() + ".fmu";
    }


    private static String getSmartServiceEndpoint(EmbeddedSmartService service, Submodel submodel) {
        // [server]:[port}/api/v3.0/{submodelId}/submodel-elements/{idShortPath}/invoke
        return String.format("/submodels/%s/submodel-elements/%s-RunSimulation",
                EncodingHelper.base64UrlEncode(submodel.getId()),
                service.getName());
    }


    private static String randomId() {
        return "id-" + UUID.randomUUID().toString().replace("-", "");
    }


    private static Submodel getSubmodel(EnvironmentContext environmentContext) {
        return environmentContext.getEnvironment().getSubmodels().stream()
                .filter(x -> ReferenceHelper.equals(x.getSemanticId(), SEMANTIC_ID_SMT_SIMULATION))
                .filter(x -> Objects.equals(x.getIdShort(), ID_SHORT_SIMULATION_MODELS))
                .findFirst()
                .orElse(null);
    }


    private static Submodel createSubmodel() {
        return new DefaultSubmodel.Builder()
                .semanticId(SEMANTIC_ID_SMT_SIMULATION)
                .id(String.format("http://modapto.eu/smart-service-container/%s", randomId()))
                .idShort(ID_SHORT_SIMULATION_MODELS)
                .build();
    }


    private static SubmodelElementCollection createSimulationModel(EmbeddedSmartService service) {
        return new DefaultSubmodelElementCollection.Builder()
                .semanticId(SEMANTIC_ID_SIMULATION_MODEL)
                .idShort(service.getName())
                .value(new DefaultSubmodelElementCollection.Builder()
                        .semanticId(SEMANTIC_ID_MODEL_FILE)
                        .idShort(ID_SHORT_MODEL_FILE)
                        .value(new DefaultSubmodelElementCollection.Builder()
                                .semanticId(SEMANTIC_ID_MODEL_FILE_VERSION)
                                .idShort(ID_SHORT_MODEL_FILE_VERSION)
                                .value(new DefaultFile.Builder()
                                        .semanticId(SEMANTIC_ID_DIGITAL_FILE)
                                        .idShort(ID_SHORT_DIGITAL_FILE)
                                        .contentType("application/octet-stream")
                                        .value(getFmuFilename(service))
                                        .build())
                                .build())
                        .build())
                .build();
    }
}
