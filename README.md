# Module & Digital Twin Management component of MODAPTO System

This is the Module & Digital Twin Management component of MODAPTO System.

## Requirements
- Java 17
- Maven (only for build)
- Docker

### DT Deployment Types
You need to support at least one of the following DT deployment types: `internal`, `docker`

**Internal** means that FA³ST Service will be started as embedded service, i.e., in the same JVM as the Module & Digital Twin Management component. For this to work, you need need locally install the latest version of the MODAPTO Digital Twin component via the following steps

- clone/donwload https://github.com/Modapto/digital-twin
- run `make build` in `/digital-twin`

**Docker** means that FA³ST Service will be started as a docker container. For this to work, you need a running docker daemon and configure it by setting the following value(s) in the application.properties

```YAML
dt.deployment.docker.host=tcp://localhost:2375 // set to match your docker daemon
```

### API
The API of the component is described in the OpenAPI format at https://github.com/Modapto/digital-twin-management/blob/main/api-interface-1.0.0-RC01.yaml

When running, you can also access the API documentation at http://localhost:8080/swagger-ui.html

### Configuration
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
