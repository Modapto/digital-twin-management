# Module & Digital Twin Management component of MODAPTO System

This is the Module & Digital Twin Management (MDT) component of MODAPTO System.

## Requirements
- Java 17
- Maven (only for build)
- Docker

## Running with docker
Module & Digital Twin Management manages Modules and Smart Service (with the three types of embedded, internal, and external) which are created at runtime.
Some of these can be run within the same JVM or as docker containers, some (internal Smart Services) can only be run a docker containers.
So for complete functionality of the DTM it needs to be able to have access to a docker daemon to execute container dynamically.
If you are running DTM in docker itself there are two options: use a docker-in-docker setup or docker socket binding (i.e. bind the host docker socket to to container).
Both have their own pros and cons.
For that reason, two docker images are published.
The `regular` one is using docker socket binding, which means you manually have to bind the docker socket e.g. by running something like this
```
docker run -v /var/run/docker.sock:/var/run/docker.sock -v $(which docker):/usr/bin/docker -it digital-twin-management:latest
```
Alternatively, in image using docker-in-docker (dind) is published with the tag starting with `dind-`, e.g. `digital-twin-management:dind-latest`.

If docker is not correctly configured, the DTM should run nonetheless but fail when rying to use features that require docker (e.g. creating modules with type `docker` or smart services with type `internal`).


## DT Deployment Types
You need to support at least one of the following DT deployment types: `internal`, `docker`

**Internal** means that FA³ST Service will be started as embedded service, i.e., in the same JVM as the Module & Digital Twin Management component. For this to work, you need need locally install the latest version of the MODAPTO Digital Twin component via the following steps

- clone/donwload https://github.com/Modapto/digital-twin
- run `make build` in `/digital-twin`

**Docker** means that FA³ST Service will be started as a docker container. For this to work, you need a running docker daemon and configure it by setting the following value(s) in the application.properties

```YAML
dt.deployment.docker.host=tcp://localhost:2375 // set to match your docker daemon
```

## API
The API can be found at `/API Interface`.
When running, you can also access the API documentation at http://localhost:8080/swagger-ui.html

## Configuration
Configuration happens via Spring framework. The typical way to configure the software is by providing an `application.properties` file.

The following configuration parameters can be used (set to default values in the example)

```YAML
### Other MODAPTO components
# The hostname of the Service Catalog component
modapto.service-catalogue.host=https://services.modapto.atc.gr

# The URL pattern to use to query details about a single service from the Service Catalog. Use %s as placeholder for the service ID.
modapto.service-catalogue.path=/micro-service-controller-rest/rest/msc/callMicroserviceCustomIO/2daf6c38-4579-4929-8d72-4d869c9bcc4e/getService?id=%s

# The URL of the Kafka server of the MessageBus component
modapto.messagebus.url=${MESSAGEBUS_KAFKA_URL:}

### DTM configuration
# Sets the deployment type of the DTM. This must be set correctly for the DTM to work (i.e. when running in docker set to 'DOCKER')
dt-management.deployment.type=${DTM_DEPLOYMENT_TYPE:INTERNAL}

# Toggles HTTP proxy server for Digital Twins
dt-management.useProxy=true

# The hostname that DTM uses when returning URLs to the outside world
dt-management.hostname=${DTM_HOSTNAME:localhost}

# The hostname that DTM uses when returning URLs to the outside world
dt-management.Port=${DTM_PORT:8080}

# Messages that are to be published via Kafka are first put in a queue and then handled asynchronously.
# Size of the queue
dt-management.kafka.queue.size=${DTM_KAFKA_QUEUE_SIZE:100}

# Number of threads propcessing the queue
dt-management.kafka.thread.count=${DTM_KAFKA_THREAD_COUNT:1}

# DT Management starts an MQTT server that all DTs publish their events to.
# Hostname used to start the MQTT server on
dt-management.events.mqtt.host=${DTM_EVENTS_MQTT_HOST:localhost}

# Port used to start the MQTT server on
dt-management.events.mqtt.port=${DTM_EVENTS_MQTT_PORT:1883}

# Messages received via MQTT are first put in a queue and then handled asynchronously.
# Size of the queue
dt-management.events.mqtt.queue.size=${DTM_EVENTS_MQTT_QUEUE_SIZE:100}

# Number of threads propcessing the queue
dt-management.events.mqtt.thread.count=${DTM_EVENTS_MQTT_THREAD_COUNT:1}

### DT deployment via Docker
# When DTs are deployed via docker, this requires to create some temp files that are then mapped into the container. When DT Management is running in docker itself and using the host docker daemon, this might create some issues with access to temp directories.
# In this case, you need to manually mount a volume to '/tmp/dt-context' to the DTM container (with write access) and you also need to provide the directory mounted in this config property.
dt.deployment.docker.tmpDirHostMapping=${DT_MOUNT_DIR:}

# Docker daemon to use when deplyoing DTs
dt.deployment.docker.host=${DOCKER_HOST:tcp://127.0.0.1:2375}

# Docker registry URL to use to fetch the DT image
dt.deployment.docker.registryUrl=${DT_DOCKER_REGISTRY_URL:ghcr.io}

# Docker registry username to use to fetch the DT image
dt.deployment.docker.registryUsername=${DT_DOCKER_REGISTRY_USERNAME:}

# Docker registry password to use to fetch the DT image
dt.deployment.docker.registryPassword=${DT_DOCKER_REGISTRY_PASSWORD:}

# Docker image to use when starting DTs
dt.deployment.docker.image=${DT_DOCKER_IMAGE:ghcr.io/modapto/digital-twin:latest}

# Smart Service Deployment
# Docker daemon to use when starting internal Smart Services
dt-management.service.internal.deployment.docker.host=${DOCKER_HOST:tcp://127.0.0.1:2375}

# Docker registry URL to use to fetch Internal Smart Service docker images
dt-management.service.internal.deployment.docker.registryUrl=${INTERNAL_SERVICE_DOCKER_REGISTRY_URL:ghcr.io}

# Docker registry username to use to fetch Internal Smart Service docker images
dt-management.service.internal.deployment.docker.registryUsername=${INTERNAL_SERVICE_DOCKER_REGISTRY_USERNAME}

# Docker registry password to use to fetch Internal Smart Service docker images
dt-management.service.internal.deployment.docker.registryPassword=${INTERNAL_SERVICE_DOCKER_REGISTRY_PASSWORD}


### System configuration
# Database cshema to use
spring.jpa.properties.eclipselink.schema=dt-management

### DB - Memory
# Configuration for in-memory H2 database
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=...
spring.datasource.password=...
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true
spring.jpa.hibernate.ddl-auto=update

### DB - Postgres
# Example on how to user a PostgreSQL database
#spring.h2.console.enabled=false
#spring.datasource.driverClassName=org.postgresql.Driver
#spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
#spring.datasource.url=jdbc:postgresql://...:5432/dtm
#spring.datasource.username=...
#spring.datasource.password=...
#spring.jpa.hibernate.ddl-auto=update

### Testing & Debugging

# Log level general
logging.level.root=INFO

# Log level Spring
logging.level.org.springframework.web=debug

# Log level JPA
spring.jpa.properties.eclipselink.logging.level=WARNING

# DT deployment type for unit tests
dt.deployment.type.default=DOCKER

# Docker Registry internal port for unit tests
dt.management.test.localDockerRegistryInternalPort=5000

# Docker Registry external port for unit tests
dt.management.test.localDockerRegistryExposedPort=5000

```
