# Module & Digital Twin Management component of MODAPTO System

This is the Module & Digital Twin Management (MDT) component of MODAPTO System.

## Requirements
- Java 17
- Maven (only for build)
- Docker

## Running with docker
Module & Digital Twin Management manages Modules and Smart Service (with the three types of embedded, internal, and external) which are created at runtime. 
Some of these can be run within the same JVM or as docker containers, some (internal Smart Services) can only be run a docker containers.
So for complete functionality of the MDT it needs to be able to have access to a docker daemon to execute container dynamically.
If you are running MDT in docker itself there are two options: use a docker-in-docker setup or docker socket binding (i.e. bind the host docker socket to to container).
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
// URL of MODAPTO Service Catalog
modapto.service-catalogue.url=http://localhost:8080

// DT deployment
dt.deployment.type.default=INTERNAL
dt.deployment.docker.host=tcp://localhost:2375
dt.deployment.docker.registryUrl=ghcr.io
dt.deployment.docker.registryUsername=
dt.deployment.docker.registryPassword=
dt.deployment.docker.image=

// Smart Service Deployment
service.internal.deployment.docker.host=tcp://localhost:2375
service.internal.deployment.docker.registryUrl=ghcr.io
service.internal.deployment.docker.registryUsername=
service.internal.deployment.docker.registryPassword=

// Database
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true
spring.jpa.hibernate.ddl-auto=update

// Logging
logging.level.org.springframework.web=debug
```
