/*
 * Copyright (c) 2021 Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e. V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.adminshell.aas.v3.dataformat.aml.util;

import com.google.common.base.Objects;
import io.adminshell.aas.v3.dataformat.core.ReflectionHelper;
import io.adminshell.aas.v3.dataformat.core.serialization.EnumSerializer;
import io.adminshell.aas.v3.model.AssetAdministrationShell;
import io.adminshell.aas.v3.model.AssetAdministrationShellEnvironment;
import io.adminshell.aas.v3.model.Identifiable;
import io.adminshell.aas.v3.model.Key;
import io.adminshell.aas.v3.model.KeyElements;
import io.adminshell.aas.v3.model.KeyType;
import io.adminshell.aas.v3.model.ModelingKind;
import io.adminshell.aas.v3.model.Referable;
import io.adminshell.aas.v3.model.Reference;
import io.adminshell.aas.v3.model.Submodel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AASUtils {

    private AASUtils() {
    }

    public static String asString(Reference reference) {
        if (reference == null) {
            return null;
        }
        return reference.getKeys().stream()
                .map(x -> String.format("(%s)[%s]%s", x.getType(), x.getIdType(), x.getValue()))
                .collect(Collectors.joining(","));
    }

    public static Optional<Submodel> resolveSubmodelReference(Reference reference, AssetAdministrationShellEnvironment environment) {
        return environment.getSubmodels().stream()
                .filter(sm -> Objects.equal(sm.getIdentification().getIdentifier(), reference.getKeys().get(0).getValue()))
                .findAny();
    }

    public static boolean isLocal(Reference reference, AssetAdministrationShellEnvironment environment) {
        // could/should additionally try resolving it
        return !reference.getKeys().stream().anyMatch(x -> x.getType() == KeyElements.GLOBAL_REFERENCE);
    }

    public static List<Submodel> getSubmodelTemplates(AssetAdministrationShell aas, AssetAdministrationShellEnvironment environment) {
        return aas.getSubmodels().stream()
                .map(ref -> resolveSubmodelReference(ref, environment))
                .filter(sm -> sm.isPresent())
                .map(sm -> sm.get())
                .filter(sm -> sm.getKind() == ModelingKind.TEMPLATE)
                .collect(Collectors.toList());
    }

    public static boolean hasTemplate(AssetAdministrationShell aas, AssetAdministrationShellEnvironment environment) {
        return !getSubmodelTemplates(aas, environment).isEmpty();
    }

    public static Referable resolve(Reference reference, AssetAdministrationShellEnvironment env) {

        List<Key> keys = reference.getKeys();

        //get reduced Key list from last identifiable key to end
        List<Key> reducedKeyList = new ArrayList<>();
        reduceKeyList(keys, reducedKeyList);

        //resolve the reference
        Referable searchedReferable = null;
        for (int i = 0; i < reducedKeyList.size(); i++) {

            Key actualKey = reducedKeyList.get(i);
            String className = EnumSerializer.translate(actualKey.getType().name());
            try {

                //get class from the key type and calculate the method name for getting a list of the elements
                //e.g. "getSubmodelElements"
                Class c = Class.forName(ReflectionHelper.MODEL_PACKAGE_NAME + "." + className);

                //TODO: visitor pattern? e.g. for Operation Variables
                String methodName = "get" + className + "s";

                Method method = null;

                if (i == 0) {
                    //first Key is identifiable

                    //get list of elements due to the key type
                    method = env.getClass().getMethod(methodName);
                    List<Object> list = (List<Object>) method.invoke(env);
                    searchedReferable = getIdentifiable(actualKey, c, list);

                } else {

                    //if searchedReferable is null then the first identifiable could not be found
                    if (searchedReferable == null) {
                        return null;
                    }

                    //get list of elements due to the key type
                    method = searchedReferable.getClass().getMethod(methodName);
                    List<Object> list = (List<Object>) method.invoke(searchedReferable);
                    searchedReferable = getReferable(actualKey, list);
                }

            } catch (ClassNotFoundException | NoSuchMethodException e) {
                e.printStackTrace();
                return null;
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return searchedReferable;
    }

    private static void reduceKeyList(List<Key> keys, List<Key> reducedKeyList) {
        for (int i = keys.size() - 1; i >= 0; i--) {
            Key k = keys.get(i);
            reducedKeyList.add(0, k);
            String className = EnumSerializer.translate(k.getType().name());
            Class c = null;
            try {
                c = Class.forName(ReflectionHelper.MODEL_PACKAGE_NAME + "." + className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (Identifiable.class.isAssignableFrom(c)) {
                break;
            }
        }
    }

    private static Referable getReferable(Key actualKey, List<Object> list) {
        Referable searchedReferable = null;
        for (Object e : list) {
            Referable element = (Referable) e;
            if (actualKey.getIdType() == KeyType.ID_SHORT) {
                if (element.getIdShort().equals(actualKey.getValue())) {
                    searchedReferable = element;
                }
            }
        }
        return searchedReferable;
    }

    private static Identifiable getIdentifiable(Key lastKey, Class c, List<Object> list) {
        if (Identifiable.class.isAssignableFrom(c)) {
            for (Object e : list) {
                Identifiable element = (Identifiable) e;
                if (lastKey.getIdType() == KeyType.ID_SHORT) {
                    if (element.getIdShort().equals(lastKey.getValue())) {
                        return element;
                    }
                } else if (lastKey.getIdType() == KeyType.IRI || lastKey.getIdType() == KeyType.IRDI || lastKey.getIdType() == KeyType.CUSTOM) {
                    if (element.getIdentification().getIdentifier().equals(lastKey.getValue())) {
                        return element;
                    }
                }
            }
        }
        return null;
    }
}
