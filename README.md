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
When running, you can also access the API documentation at /api/dtm/swagger

## Examples

In /examples you can find two docker-compose yaml files `docker-compose.yml` and `docker-compose-remote-service-catalog.yml`.
Similar to the compose files, you also find Postman collections `DT Management Test Local.postman_collection.json` and `DT Management Test Local With Remote Service Catalog.postman_collection.json`.

With the first one, you can start a fully local docker compose containing DTM with a Postgres database, Kafka, and a service catalog mock.
With the second one, you start almost the same but it is configured to use the live service catalog.

Before running any compose file, you need to update the `.env` file with at least the current dir on your local system (i.e. the absolute path where the `examples` folder resides including /examples at the end).
You may also need to provide docker credentials in case docker needs to fetch some of the images.

Once a compose file is up and running, open the corresponding Postman collection and start running the request in order.

## Events
DT Management publishes events to Kafka. The following event topics are available

- modapto-module-creation
- modapto-module-update
- modapto-module-deletion
- smart-service-assigned
- smart-service-unassigned
- smart-service-invoke
- smart-service-finish

Example payloads for these events can be found at `src\test\resources\event`.


## Configuration
Configuration happens via Spring framework.
The typical way to configure the software is by providing an `application.properties` file.
You can also override configuration properties using environment variables [as described in the Spring documentation](https://docs.spring.io/spring-boot/docs/2.1.x/reference/html/boot-features-external-config.html#boot-features-external-config-relaxed-binding-from-environment-variables).

The following configuration parameters can be used (set to default values in the example)

```YAML
### SECURITY
spring.security.oauth2.resourceserver.jwt.issuer-uri=
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/certs

# If true, all calls to DTs via DTM as proxy are secured, if false calls will be available without authentication
dt-management.security.secureProxyDTs=true

### MODAPTO
# The hostname of the Service Catalog component
modapto.service-catalogue.host=https://services.modapto.atc.gr

# The URL pattern to use to query details about a single service from the Service Catalog. Use %s as placeholder for the service ID.
modapto.service-catalogue.path=/micro-service-controller-rest/rest/msc/callMicroserviceCustomIO/2daf6c38-4579-4929-8d72-4d869c9bcc4e/getService?id=%s

# The URL of the Kafka server of the MessageBus component
modapto.messagebus.url=${MESSAGEBUS_KAFKA_URL:}

### DTM
# Toggles HTTP proxy server for Digital Twins
dt-management.useProxy=true

# If true, DTs are exposed using the container name and internal port, otherwise using hostname and the mapped port. This only takes effect if `dt-management.useProxy=false`
dt-management.exposeDTsViaContainerName=false

# Sets the deployment type of the DTM. This must be set correctly for the DTM to work (i.e. when running in docker set to 'DOCKER')
dt-management.deployment.type=INTERNAL

# The hostname that DTM uses when returning URLs to the outside world
dt-management.hostname=localhost

# The name of the docker container DTM is running in. If not running inside a docker container this property is ignored.
dt-management.docker.container.name=${HOSTNAME:}

# The name of the docker network the DTM is connected to. If not running inside a docker container this property is ignored.
dt-management.docker.network=

# The port that DTM uses when returning URLs to the outside world
dt-management.port=8080

# The timeout for checking the liveliness after starting new DTs (in ms)
dt-management.deployment.liveliness-check.timeout=100000

# The interval for checking the liveliness after starting new DTs (in ms)
dt-management.deployment.liveliness-check.interval=500

# Messages that are to be published via Kafka are first put in a queue and then handled asynchronously.
# Size of the queue
dt-management.kafka.queue.size=100

# Number of threads propcessing the queue
dt-management.kafka.thread.count=1

# DT Management starts an MQTT server that all DTs publish their events to.
# Hostname used to start the MQTT server on
dt-management.events.mqtt.host=localhost

# Maximum MQTT message size. Default 256MB (max allowed according to MQTT specification)
dt-management.events.mqtt.max-message-size:268435456

# Port used to start the MQTT server on
dt-management.events.mqtt.port=1883

# DTs connect to the MQTT server embedded in DTM. When both are running in docker, this property specifies the hostname under which the DT can contact the DTM. If not provided, the docker container name from `dt-management.docker.container.name` is used.
dt-management.events.mqtt.host-from-container=

# Messages received via MQTT are first put in a queue and then handled asynchronously.
# Size of the queue
dt-management.events.mqtt.queue.size=100

# Number of threads propcessing the queue
dt-management.events.mqtt.thread.count=1

### DT
# Docker image to use when starting DTs
dt.deployment.docker.image=ghcr.io/modapto/digital-twin:latest

# Restart policy for docker containers (module and internal smart services)
dt.deployment.docker.restartPolicy=unless-stopped

# Prefix for docker container names for MODAPTO modules
dt.deployment.docker.moduleContainerPrefix=modapto-module-

# Prefix for docker container names for (internal) smart services
dt.deployment.docker.serviceContainerPrefix=modapto-service-

###
# If true, embedded service calls return results for each step, otherwise only for last step (=final result)
modapto.embedded-service.returnResultsForEachStep=true

### DB MEMORY
# Configuration for in-memory H2 database
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=...
spring.datasource.password=...
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true
spring.jpa.hibernate.ddl-auto=update

### DB POSTGRES
# Configuration for PostgreSQL database
spring.h2.console.enabled=false
spring.datasource.driverClassName=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.datasource.url=jdbc:postgresql://...:5432/dtm
spring.datasource.username=...
spring.datasource.password=...
spring.jpa.hibernate.ddl-auto=update

### LOGGING
logging.level.root=INFO
logging.level.eu.modapto.digitaltwinmanagement=INFO
logging.level.org.springframework.web=INFO
spring.jpa.properties.eclipselink.logging.level=WARNING

# Toggles if logs from docker containers started by DTM should be included in the DTM log. This is helpful when you do not have access to the logs of the container directly.
dt-management.includeDockerLogs=false

### SYSTEM
# Database schema to use
spring.jpa.properties.eclipselink.schema=dt-management

# Specifies the logging pattern for console logging
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} %-5(%level) %-26.26(%.-25([%logger{0})]) : %msg%n
```

## Changelog

<!--changelog-anchor-->
<!--start:changelog-header-->
## 0.6.0-SNAPSHOT (current development version)<!--end:changelog-header-->
- enable authentication with service catalog by forwarding JWT from user request to service catalog

### Internal changes & bugfixes
- make property `name` optional when assigning a smart service

## 0.5

Initial release
