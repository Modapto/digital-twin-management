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
package eu.modapto.digitaltwinmanagement.model.security;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;


@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_RESOURCE_ACCESS = "resource_access";
    private static final String CLAIM_ROLES = "roles";

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) throws NullPointerException {
        Collection<GrantedAuthority> authorities = Stream.concat(
                Optional.of(jwtGrantedAuthoritiesConverter.convert(jwt)).orElseGet(Collections::emptyList).stream(),
                extractKeycloakRoles(jwt).stream()).collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaim("preferred_username"));
    }


    @SuppressWarnings("unchecked")
    private List<SimpleGrantedAuthority> extractKeycloakRoles(Jwt jwt) {
        try {
            Set<String> roles = new HashSet<>();

            // Extract realm roles
            Map<String, Object> realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);
            if (realmAccess != null) {
                roles.addAll(extractRolesFromClaim(realmAccess));
            }

            // Extract resource roles
            Map<String, Object> resourceAccess = jwt.getClaim(CLAIM_RESOURCE_ACCESS);
            if (resourceAccess != null) {
                resourceAccess.values().stream()
                        .filter(Map.class::isInstance)
                        .map(obj -> (Map<String, Object>) obj)
                        .map(this::extractRolesFromClaim)
                        .forEach(roles::addAll);
            }

            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();

        }
        catch (Exception e) {
            return Collections.emptyList();
        }
    }


    private List<String> extractRolesFromClaim(Map<String, Object> claimMap) {
        Object rolesObj = claimMap.get(CLAIM_ROLES);
        if (rolesObj instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return Collections.emptyList();
    }

}
