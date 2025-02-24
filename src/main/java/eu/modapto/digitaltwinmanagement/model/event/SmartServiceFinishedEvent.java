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

import eu.modapto.digitaltwinmanagement.model.event.payload.SmartServiceFinishedPayload;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
public class SmartServiceFinishedEvent extends AbstractEvent<SmartServiceFinishedPayload> {
    private static final Priority PRIORITY = Priority.LOW;
    private static final String SOURCE_COMPONENT = "DT";
    private static final String EVENT_TYPE = "Smart Service finished";
    private static final String TOPIC = "smart-service-finish";

    private SmartServiceFinishedEvent() {
        super(PRIORITY, SOURCE_COMPONENT, EVENT_TYPE, TOPIC);
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractEvent.AbstractBuilder<SmartServiceFinishedPayload, SmartServiceFinishedEvent, Builder> {

        @Override
        protected Builder getSelf() {
            return this;
        }


        @Override
        protected SmartServiceFinishedEvent newBuildingInstance() {
            return new SmartServiceFinishedEvent();
        }


        @Override
        public Builder payload(SmartServiceFinishedPayload value) {
            getBuildingInstance().setSmartService(value.getServiceId());
            return super.payload(value);
        }
    }
}
