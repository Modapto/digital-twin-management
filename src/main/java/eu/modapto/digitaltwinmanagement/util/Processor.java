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

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Processor<T> implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Processor.class);

    public enum Status {
        CREATED,
        WAITING,
        WORKING,
        STOPPED
    }

    private final BlockingQueue<T> queue;
    private final Consumer<T> consumer;
    private final String name;
    private Status status = Status.CREATED;
    private Instant workStarted;

    public Processor(BlockingQueue<T> queue, Consumer<T> consumer, String name) {
        if (queue == null) {
            throw new IllegalArgumentException("queue must be non-null");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("handler must be non-null");
        }
        if (name == null || name.isEmpty()) {
            this.name = getClass().getName();
        }
        else {
            this.name = name;
        }
        this.queue = queue;
        this.consumer = consumer;
    }


    @Override
    public void run() {
        LOGGER.debug("starting {}-Thread", name);
        while (!Thread.currentThread().isInterrupted()) {
            T event;
            try {
                status = Status.WAITING;
                event = queue.take();
                status = Status.WORKING;
                workStarted = Instant.now();
                consumer.accept(event);
            }
            catch (InterruptedException ex) {
                LOGGER.trace("{} interrupted", name, ex);
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception ex) {
                LOGGER.warn("Exception while executing {}", name, ex);
            }
        }
        status = Status.STOPPED;
        LOGGER.debug("exiting {}-Thread", name);
    }


    public Status getStatus() {
        return status;
    }


    public boolean isFine(Instant threshold) {
        if (status != Status.WORKING) {
            return true;
        }
        return workStarted.isAfter(threshold);
    }
}
