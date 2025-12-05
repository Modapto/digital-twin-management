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

import de.fraunhofer.iosb.ilt.faaast.service.util.Ensure;
import java.util.UUID;


public class IdHelper {

    private IdHelper() {}


    public static String uuid() {
        return UUID.randomUUID().toString();
    }


    public static String uuidAlphanumeric() {
        return uuid().replace("-", "");
    }


    public static String uuidAlphanumeric(String uuid) {
        return uuid.replace("-", "");
    }


    public static String uuidAlphanumeric(int length) {
        Ensure.require(length > 0 && length <= 32, "length must be between 0 and 32");
        return uuidAlphanumeric().substring(0, length);
    }


    public static String uuidAlphanumeric(String uuid, int length) {
        Ensure.require(length > 0 && length <= 32, "length must be between 0 and 32");
        return uuidAlphanumeric(uuid).substring(0, length);
    }


    public static String uuidAlphanumeric8(String uuid) {
        return uuidAlphanumeric(uuid, 8);
    }


    public static String uuidAlphanumeric8() {
        return uuidAlphanumeric(8);
    }


    public static String uuidAlphanumeric16() {
        return uuidAlphanumeric(16);
    }


    public static String uuidAlphanumeric16(String uuid) {
        return uuidAlphanumeric(uuid, 16);
    }
}
