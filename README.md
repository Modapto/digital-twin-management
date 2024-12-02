# Module & Digital Twin Management component of MODAPTO System

This is the Module & Digital Twin Management component of MODAPTO System.

## Requirements
- Java 17
- Maven (only for build)

### DT Deployment Types
You need to support at least one of the following DT deployment types: `internal`, `docker`

**Internal** means that FA³ST Service will be started as embedded service, i.e., in the same JVM as the Module & Digital Twin Management component. For this to work, you need need locally install the latest version of the MODAPTO Digital Twin component via the following steps

- clone/donwload https://github.com/Modapto/digital-twin
- run `make build` in `/digital-twin`

**Docker** means that FA³ST Service will be started as a docker container. For this to work, you need a running docker daemon and configure it by setting the following value(s) in the application.properties

````YAML
dt.deployment.docker.host=tcp://localhost:2375 // set to match your docker daemon
```

The API of the component is described in the OpenAPI format at https://github.com/Modapto/digital-twin-management/blob/main/api-interface-v1.0.0-SNAPSHOT-2024-12.02.yaml
