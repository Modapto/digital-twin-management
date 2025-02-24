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
package eu.modapto.digitaltwinmanagement.model.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.digitaltwin.aas4j.v3.model.builder.ExtendableBuilder;


@Getter
@Setter(value = AccessLevel.PROTECTED)
@EqualsAndHashCode
public abstract class AbstractEvent<T> {

    private final Priority priority;
    private final String sourceComponent;
    private final String eventType;
    private final String topic;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    protected LocalDateTime timestamp;
    @JsonProperty("module")
    protected String moduleId;
    protected String smartService;
    @JsonProperty("results")
    protected T payload;

    protected AbstractEvent(Priority priority, String sourceComponent, String eventType, String topic) {
        this.timestamp = LocalDateTime.now();
        this.priority = priority;
        this.sourceComponent = sourceComponent;
        this.eventType = eventType;
        this.topic = topic;
    }

    protected abstract static class AbstractBuilder<T, P extends AbstractEvent<T>, B extends AbstractBuilder<T, P, B>> extends ExtendableBuilder<P, B> {

        public B moduleId(String value) {
            getBuildingInstance().setModuleId(value);
            return getSelf();
        }


        public B payload(T value) {
            getBuildingInstance().setPayload(value);
            return getSelf();
        }


        public B timestamp(LocalDateTime value) {
            getBuildingInstance().setTimestamp(value);
            return getSelf();
        }
    }
}
