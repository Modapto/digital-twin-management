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
package eu.modapto.digitaltwinmanagement.model;

import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import eu.modapto.digitaltwinmanagement.exception.NetworkAddressException;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;


@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class Address {
    private static final String PROTOCOL_SEPARATOR = "://";
    private static final String PREFIX_HTTP = "http" + PROTOCOL_SEPARATOR;

    private String host;
    private String path;
    private int port;

    public Address(String value) {
        try {
            URL url = new URL(value);
            host = String.format("%s://%s", url.getProtocol(), url.getHost());
            path = url.getFile();
            port = url.getPort();
        }
        catch (MalformedURLException e) {
            throw new NetworkAddressException(String.format("failed to parse URL (%s)", value), e);
        }
    }


    public String asUrl() {
        String result = host;
        if (port > 0) {
            result += ":" + port;
        }
        if (!StringHelper.isBlank(path)) {
            result += path;
        }
        return ensureProtocolPresent(result);
    }


    public String getBaseUrl() {
        String result = host;
        if (port > 0) {
            result += ":" + port;
        }
        return ensureProtocolPresent(result);
    }


    private static String ensureProtocolPresent(String url) {
        if (url.contains(PROTOCOL_SEPARATOR)) {
            return url;
        }
        return PREFIX_HTTP + url;
    }


    @Override
    public String toString() {
        return asUrl();
    }
}
