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
import de.fraunhofer.iosb.ilt.faaast.service.model.submodeltemplate.Cardinality;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.model.EmbeddedSmartService;
import eu.modapto.dt.faaast.service.smt.simulation.FmuHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import no.ntnu.ihb.fmi4j.importer.fmi2.Fmu;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.aasx.InMemoryFile;
import org.eclipse.digitaltwin.aas4j.v3.model.AasSubmodelElements;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.QualifierKind;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultFile;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultLangStringTextType;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperation;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultQualifier;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EmbeddedSmartServiceHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedSmartServiceHelper.class);

    private static final Reference SEMANTIC_ID_SMT_SIMULATION = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/SimulationModels/1/0");
    private static final Reference SEMANTIC_ID_SIMULATION_MODEL = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/SimulationModel/1/0");
    private static final Reference SEMANTIC_ID_MODEL_FILE = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/ModelFile/1/0");
    private static final Reference SEMANTIC_ID_MODEL_FILE_VERSION = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/ModelFileVersion/1/0");
    private static final Reference SEMANTIC_ID_DIGITAL_FILE = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/DigitalFile/1/0");

    private static final String ID_SHORT_SIMULATION_MODELS = "SimulationModels";
    private static final String ID_SHORT_MODEL_FILE = "ModelFile";
    private static final String ID_SHORT_MODEL_FILE_VERSION = "ModelFileVersion";
    private static final String ID_SHORT_DIGITAL_FILE = "DigitalFile";
    private static final String ID_SHORT_STEP_ARGUMENTS_TEMPLATE = "stepArgumentsTemplate";
    private static final String ID_SHORT_STEP_RESULTS_TEMPLATE = "stepResultsTemplate";

    private static final String ARG_CURRENT_TIME_ID = "currentTime";
    private static final String ARG_TIME_STEP_ID = "timeStep";
    private static final String ARG_STEP_NUMBER_ID = "stepNumber";
    private static final String ARG_STEP_COUNT_ID = "stepCount";
    private static final String ARG_ARGS_PER_STEP_ID = "argumentsPerStep";
    private static final String ARG_RESULT_PER_STEP_ID = "resultPerStep";

    private static final String SMC_SIMULATION_MODELS_PREFIX = "SimulationModel_";

    private static final OperationVariable ARG_CURRENT_TIME = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_CURRENT_TIME_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("name of the newly created instance")
                            .build())
                    .valueType(DataTypeDefXsd.STRING)
                    .build())
            .build();

    private static final OperationVariable ARG_TIME_STEP = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_TIME_STEP_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("time step size")
                            .build())
                    .valueType(DataTypeDefXsd.DOUBLE)
                    .build())
            .build();

    private static final OperationVariable ARG_STEP_COUNT = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_STEP_COUNT_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("number of steps to execute")
                            .build())
                    .valueType(DataTypeDefXsd.INTEGER)
                    .build())
            .build();

    private EmbeddedSmartServiceHelper() {}


    private static void initializeOperation(Operation operation, EmbeddedSmartService service) {
        try {
            Fmu fmu = FmuHelper.loadFmu(service.getName(), service.getFmu());

            List<OperationVariable> inputVariables = new ArrayList<>(List.of(
                    ARG_CURRENT_TIME,
                    ARG_TIME_STEP,
                    ARG_STEP_COUNT,
                    newMultiStepArg(FmuHelper.getInputArgumentsMetadata(fmu))));
            if (Objects.nonNull(service.getInputParameters()) && !service.getInputParameters().isEmpty()) {
                inputVariables.removeIf(x -> service.getInputParameters().stream()
                        .noneMatch(y -> Objects.equals(y.getIdShort(), x.getValue().getIdShort())));
            }
            List<OperationVariable> outputVariables = new ArrayList<>(List.of(getMultiStepResult(fmu, service)));
            operation.setIdShort(service.getName());
            operation.setInputVariables(inputVariables);
            operation.setOutputVariables(outputVariables);
            service.setInputParameters(inputVariables.stream().map(OperationVariable::getValue).toList());
            service.setOutputParameters(outputVariables.stream().map(OperationVariable::getValue).toList());
        }
        catch (Exception e) {
            LOGGER.debug("Error loading FMU file", e);
            throw new IllegalArgumentException(String.format("Error loading FMU file (reason: %s)", e.getMessage()));
        }
    }


    private static String simulationModelName(EmbeddedSmartService service) {
        return SMC_SIMULATION_MODELS_PREFIX + service.getName();
    }


    public static Operation addSmartService(EnvironmentContext environmentContext, Submodel submodel, EmbeddedSmartService service) {
        if (!ReferenceHelper.equals(SEMANTIC_ID_SMT_SIMULATION, submodel.getSemanticId())) {
            submodel.setSemanticId(SEMANTIC_ID_SMT_SIMULATION);
        }
        submodel.getSubmodelElements().add(createSimulationModel(service));
        environmentContext.getFiles().add(new InMemoryFile(service.getFmu(), getFmuFilename(service)));

        Operation operation = submodel.getSubmodelElements().stream()
                .filter(Operation.class::isInstance)
                .filter(x -> Objects.equals(service.getName(), x.getIdShort()))
                .map(Operation.class::cast)
                .findFirst()
                .orElse(new DefaultOperation());
        if (StringHelper.isEmpty(operation.getIdShort())) {
            submodel.getSubmodelElements().add(operation);
        }
        initializeOperation(operation, service);
        return operation;
    }


    private static OperationVariable newMultiStepArg(List<OperationVariable> originalArgs) {
        return new DefaultOperationVariable.Builder()
                .value(new DefaultSubmodelElementList.Builder()
                        .idShort(ARG_ARGS_PER_STEP_ID)
                        .typeValueListElement(AasSubmodelElements.SUBMODEL_ELEMENT_COLLECTION)
                        .value(new DefaultSubmodelElementCollection.Builder()
                                .idShort(ID_SHORT_STEP_ARGUMENTS_TEMPLATE)
                                .value(
                                        Stream.concat(
                                                Stream.of(new DefaultProperty.Builder()
                                                        .idShort(ARG_STEP_NUMBER_ID)
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .build()),
                                                originalArgs.stream().map(OperationVariable::getValue))
                                                .toList())
                                .qualifiers(new DefaultQualifier.Builder()
                                        .kind(QualifierKind.TEMPLATE_QUALIFIER)
                                        .valueType(DataTypeDefXsd.STRING)
                                        .value(Cardinality.ZERO_TO_MANY.getNameForSerialization())
                                        .type(Cardinality.class.getSimpleName())
                                        .build())
                                .build())
                        .build())
                .build();
    }


    private static OperationVariable getMultiStepResult(Fmu fmu, EmbeddedSmartService service) {
        List<SubmodelElement> fmuOutputs = FmuHelper.getOutputArgumentsMetadata(fmu).stream().map(OperationVariable::getValue).toList();
        List<SubmodelElement> userOutputs = Optional.ofNullable(service.getOutputParameters()).orElse(List.of());
        List<SubmodelElement> actualOutputs = new ArrayList<>();
        for (var element: userOutputs) {
            if (fmuOutputs.stream().anyMatch(x -> Objects.equals(x.getIdShort(), element.getIdShort()))) {
                actualOutputs.add(element);
            }
            else {
                LOGGER.warn("user provided output argument for embedded service is not defined in the FMU and therefore will be ignored (parameter: {})", element.getIdShort());
            }
        }
        if (actualOutputs.isEmpty()) {
            actualOutputs = fmuOutputs;
        }
        return new DefaultOperationVariable.Builder()
                .value(new DefaultSubmodelElementList.Builder()
                        .idShort(ARG_RESULT_PER_STEP_ID)
                        .typeValueListElement(AasSubmodelElements.SUBMODEL_ELEMENT_COLLECTION)
                        .value(new DefaultSubmodelElementCollection.Builder()
                                .idShort(ID_SHORT_STEP_RESULTS_TEMPLATE)
                                .value(
                                        Stream.concat(
                                                Stream.of(new DefaultProperty.Builder()
                                                        .idShort(ARG_STEP_NUMBER_ID)
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .build()),
                                                actualOutputs.stream())
                                                .toList())
                                .build())
                        .build())
                .build();
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
                    "No simulation model found in AAS model for service (service id: %s, service name: %s)",
                    service.getId(),
                    service.getName()));
        }
        submodel.getSubmodelElements().remove(simulationModel.get());
        environmentContext.getFiles().removeIf(x -> Objects.equals(x.getPath(), getFmuFilename(service)));
        service.setReference(null);
    }


    private static String getFmuFilename(EmbeddedSmartService service) {
        return service.getName() + ".fmu";
    }


    private static Submodel getSubmodel(EnvironmentContext environmentContext) {
        return environmentContext.getEnvironment().getSubmodels().stream()
                .filter(x -> ReferenceHelper.equals(x.getSemanticId(), SEMANTIC_ID_SMT_SIMULATION))
                .filter(x -> Objects.equals(x.getIdShort(), ID_SHORT_SIMULATION_MODELS))
                .findFirst()
                .orElse(null);
    }


    private static SubmodelElementCollection createSimulationModel(EmbeddedSmartService service) {
        return new DefaultSubmodelElementCollection.Builder()
                .semanticId(SEMANTIC_ID_SIMULATION_MODEL)
                .idShort(simulationModelName(service))
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
