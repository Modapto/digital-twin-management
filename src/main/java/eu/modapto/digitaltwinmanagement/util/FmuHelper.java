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

import static no.ntnu.ihb.fmi4j.modeldescription.variables.VariableType.STRING;
import static org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd.BOOLEAN;
import static org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd.DOUBLE;
import static org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd.INT;
import static org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd.INTEGER;
import static org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd.STRING;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import no.ntnu.ihb.fmi4j.Fmi4jVariableUtils;
import no.ntnu.ihb.fmi4j.importer.fmi2.CoSimulationFmu;
import no.ntnu.ihb.fmi4j.importer.fmi2.CoSimulationSlave;
import no.ntnu.ihb.fmi4j.importer.fmi2.Fmu;
import no.ntnu.ihb.fmi4j.modeldescription.CoSimulationModelDescription;
import no.ntnu.ihb.fmi4j.modeldescription.variables.Causality;
import no.ntnu.ihb.fmi4j.modeldescription.variables.ModelVariables;
import no.ntnu.ihb.fmi4j.modeldescription.variables.TypedScalarVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.VariableType;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultLangStringTextType;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Helper class to work with FMU models and files.
 *
 * @see <a href="https://fmi-standard.org/">https://fmi-standard.org/</a>
 */
public class FmuHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FmuHelper.class);

    /**
     * Gets the output arguments with current values from the fmuInstance.
     *
     * @param fmuInstance the FMU instance to read the values from
     * @return the output variables of the FMU as AAS arguments to return as result of an operation
     */
    public static List<OperationVariable> getOutputArgumentsWithValues(CoSimulationSlave fmuInstance) {
        return fmuInstance.getModelVariables().getByCausality(Causality.OUTPUT).stream()
                .map(x -> (OperationVariable) new DefaultOperationVariable.Builder()
                        .value(new DefaultProperty.Builder()
                                .idShort(x.getName())
                                .valueType(convertDatatypeFromFmuToAas(x.getType()))
                                .value(Fmi4jVariableUtils.read(x, fmuInstance).getValue().toString())
                                .build())
                        .build())
                .collect(Collectors.toList());
    }


    /**
     * Creates an instance a the fmu.
     *
     * @param name name of the instance
     * @param fmu the FMU
     * @param initial the input to set after creating the instance
     * @return new FMU instance with initial arguments
     */
    public static CoSimulationSlave createInstance(String name, Fmu fmu, List<OperationVariable> initial) {
        CoSimulationFmu fmuSimulation = fmu.asCoSimulationFmu();
        LOGGER.debug("creating new FMU instance... (name: {})", name);
        CoSimulationSlave instance = fmuSimulation.newInstance();
        LOGGER.debug("initializing new FMU instance... (name: {})", name);
        instance.simpleSetup();
        setFmuInputVariablesFromAas(instance, initial);
        return instance;
    }


    /**
     * Reads input arguments from a FMU and converts them to AAS arguments. This method reads only metadata such as name and
     * datatype but not the actual value.
     *
     * @param fmu the FMU
     * @return the input arguments in AAS metamodel
     */
    public static List<OperationVariable> getInputArgumentsMetadata(Fmu fmu) {
        return getArgumentsByCausality(fmu, Causality.INPUT, Causality.OUTPUT);
    }


    /**
     * Reads inoutput arguments from a FMU and converts them to AAS arguments. This method reads only metadata such as name
     * and datatype but not the actual value.
     *
     * @param fmu the FMU
     * @return the inoutput arguments in AAS metamodel
     */
    public static List<OperationVariable> getInoutputArgumentsMetadata(Fmu fmu) {
        ModelVariables modelVariables = fmu.getModelDescription().getModelVariables();
        List<TypedScalarVariable<?>> input = modelVariables.getByCausality(Causality.INPUT);
        List<TypedScalarVariable<?>> output = modelVariables.getByCausality(Causality.OUTPUT);
        return input.stream()
                .filter(x -> output.stream()
                        .anyMatch(y -> Objects.equals(x.getName(), y.getName())
                                && Objects.equals(x.getType(), y.getType())))
                .map(FmuHelper::variableToProperty)
                .collect(Collectors.toList());
    }


    /**
     * Reads output arguments from a FMU and converts them to AAS arguments. This method reads only metadata such as name
     * and datatype but not the actual value.
     *
     * @param fmu the FMU
     * @return the out arguments in AAS metamodel
     */
    public static List<OperationVariable> getOutputArgumentsMetadata(Fmu fmu) {
        return getArgumentsByCausality(fmu, Causality.OUTPUT, Causality.INPUT);
    }


    /**
     * Loads a FMU from byte[].
     *
     * @param name name of the FMU
     * @param fmuBinary binary content containing the FMU
     * @return the loaded FMU
     * @throws IOException if loading fails
     */
    public static Fmu loadFmu(String name, byte[] fmuBinary) throws IOException {
        return Fmu.Companion.from(name, fmuBinary);
    }


    /**
     * Loads a FMU from byte[] with random generated name.
     *
     * @param fmuBinary binary content containing the FMU
     * @return the loaded FMU
     * @throws IOException if loading fails
     */
    public static Fmu loadFmu(byte[] fmuBinary) throws IOException {
        return loadFmu(UUID.randomUUID().toString(), fmuBinary);
    }


    /**
     * Sets the current value of FMU input variables from AAS metamodel. Values in input that are not present as input
     * variables in the FMU are ignored.
     *
     * @param instance the FMU instance
     * @param input the AAS input variables containing the values to set
     */
    public static void setFmuInputVariablesFromAas(CoSimulationSlave instance, List<OperationVariable> input) {
        if (Objects.isNull(input)) {
            return;
        }
        CoSimulationModelDescription modelDescription = instance.getModelDescription();
        List<String> fmuInputs = modelDescription.getModelVariables().getByCausality(Causality.INPUT).stream()
                .map(TypedScalarVariable::getName)
                .toList();
        for (OperationVariable variable: input) {
            if (Objects.isNull(variable)
                    || Objects.isNull(variable.getValue())
                    || !Property.class.isAssignableFrom(variable.getValue().getClass())) {
                continue;
            }
            Property property = ((Property) variable.getValue());
            if (!fmuInputs.contains(property.getIdShort())) {
                continue;
            }
            TypedScalarVariable fmuVariable = modelDescription.getVariableByName(property.getIdShort());
            switch (property.getValueType()) {
                case BOOLEAN -> {
                    instance.writeBoolean(
                            new long[] {
                                    fmuVariable.getValueReference()
                            },
                            new boolean[] {
                                    Boolean.parseBoolean(property.getValue())
                            });
                }
                case DOUBLE -> {
                    instance.writeReal(
                            new long[] {
                                    fmuVariable.getValueReference()
                            },
                            new double[] {
                                    Double.parseDouble(property.getValue())
                            });
                }
                case INT, INTEGER -> {
                    instance.writeInteger(
                            new long[] {
                                    fmuVariable.getValueReference()
                            },
                            new int[] {
                                    Integer.parseInt(property.getValue())
                            });
                }
                case STRING -> {
                    instance.writeString(
                            new long[] {
                                    fmuVariable.getValueReference()
                            },
                            new String[] {
                                    property.getValue()
                            });
                }
                default -> {
                    throw new IllegalArgumentException(String.format("Unsupported property type: %s", property.getValueType()));
                }
            }
            if (!instance.getLastStatus().isOK()) {
                throw new RuntimeException(String.format(String.format("Setting input value on FMU failed (name: %s)", property.getIdShort())));
            }
        }
    }


    /**
     * Converts datatypes from FMU to AAS.
     *
     * @param fmuType the FMU datatye to convert
     * @return the corresponding AAS datatype
     * @throws IllegalArgumentException if conversion fails, e.g., because there is no type mapping
     */
    public static DataTypeDefXsd convertDatatypeFromFmuToAas(VariableType fmuType) {
        switch (fmuType) {
            case STRING -> {
                return DataTypeDefXsd.STRING;
            }
            case BOOLEAN -> {
                return DataTypeDefXsd.BOOLEAN;
            }
            case INTEGER -> {
                return DataTypeDefXsd.INTEGER;
            }
            case REAL -> {
                return DataTypeDefXsd.DOUBLE;
            }
            default -> throw new IllegalArgumentException(String.format("unsupported FMU datatype: %s", fmuType));
        }
    }


    private static OperationVariable variableToProperty(TypedScalarVariable<?> variable) {
        return new DefaultOperationVariable.Builder()
                .value(new DefaultProperty.Builder()
                        .idShort(variable.getName())
                        .description(new DefaultLangStringTextType.Builder()
                                .language("en")
                                .text(variable.getDescription())
                                .build())
                        .valueType(convertDatatypeFromFmuToAas(variable.getType()))
                        .value(Objects.toString(variable.getInitial(), null))
                        .build())
                .build();
    }


    private static List<OperationVariable> getArgumentsByCausality(Fmu fmu, Causality causality, Causality excludedCausality) {
        ModelVariables modelVariables = fmu.getModelDescription().getModelVariables();
        List<TypedScalarVariable<?>> included = modelVariables.getByCausality(causality);
        List<TypedScalarVariable<?>> excluded = modelVariables.getByCausality(excludedCausality);
        return included.stream()
                .filter(x -> excluded.stream()
                        .noneMatch(y -> Objects.equals(x.getName(), y.getName())
                                && Objects.equals(x.getType(), y.getType())))
                .map(FmuHelper::variableToProperty)
                .collect(Collectors.toList());
    }

}
