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
package eu.modapto.digitaltwinmanagement.messagebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.modapto.digitaltwinmanagement.config.KafkaConfig;
import eu.modapto.digitaltwinmanagement.model.event.AbstractEvent;
import eu.modapto.digitaltwinmanagement.util.Processor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Component
public class KafkaBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaBridge.class);
    private BlockingQueue<AbstractEvent> eventQueue;
    private ExecutorService executorService;

    @Autowired
    private KafkaConfig kafkaConfig;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper mapper;

    @PostConstruct
    public void init() {
        eventQueue = new ArrayBlockingQueue<>(kafkaConfig.getQueueSize());
        executorService = Executors.newFixedThreadPool(kafkaConfig.getThreadCount());
        for (int i = 0; i < kafkaConfig.getThreadCount(); i++) {
            executorService.submit(new Processor<>(eventQueue, this::publishToKafka, "kafka-producer"));
        }
    }


    public void publish(AbstractEvent event) {
        if (!eventQueue.offer(event)) {
            LOGGER.error("Failed to add event to event queue");
        }
        LOGGER.trace("event queued for Kafka (type: {})", event.getClass().getSimpleName());
    }


    private void publishToKafka(AbstractEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), mapper.writeValueAsString(event));
            LOGGER.trace("event published on Kafka (type: {})", event.getClass().getSimpleName());
        }
        catch (Exception e) {
            LOGGER.warn("failed to publish event to Kafka (reason: {})", e.getMessage(), e);
        }

    }


    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
        try {
            if (executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                return;
            }
        }
        catch (InterruptedException ex) {
            LOGGER.error("Interrupted while waiting for shutdown.", ex);
            Thread.currentThread().interrupt();
        }
        List<Runnable> list = executorService.shutdownNow();
        LOGGER.warn("There were {} messages left on the Kafka queue.", list.size());
    }

}
