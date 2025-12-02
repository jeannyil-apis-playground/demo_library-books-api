# books-api-v2

This project leverages [**Red Hat build of Quarkus 3.27.x**](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.27), the Supersonic Subatomic Java Framework. More specifically, the project is implemented using [**Red Hat build of Apache Camel v4.14.x for Quarkus**](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14#Red%20Hat%20build%20of%20Apache%20Camel%20for%20Quarkus).

This project implements a simple REST API that manages books. The following endpoints are exposed:
- `/api/v2/books` : 
    - `GET` method returns a list of all `Books-v2` entities.
    - `POST` method adds a new `book-v2` entity in the inventory.
- `/q/openapi` _on a separate management interface (port **9876**)_ : returns the Open API Schema document of the service.
- `/q/swagger-ui` _on a separate management interface (port **9876**)_ :  opens the Open API UI.
- `/observe/health` _on a separate management interface (port **9876**)_ : returns the _Camel Quarkus MicroProfile_ health checks.
- `/observe/metrics` _on a separate management interface (port **9876**)_ : the _Camel Quarkus Micrometer_ metrics in prometheus format.

## Prerequisites

- Apache Maven 3.9.9
- JDK 21 installed with `JAVA_HOME` configured appropriately
- A running [_Red Hat OpenShift 4_](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster
- **OPTIONAL**: [**Jaeger**](https://www.jaegertracing.io/), a distributed tracing system for observability ([_open tracing_](https://opentracing.io/)).  :bulb: A simple way of starting a Jaeger tracing server is with `docker` or `podman`:
    1. Start the Jaeger tracing server:
        ```
        podman run --rm -e COLLECTOR_ZIPKIN_HOST_PORT=:9411 -e COLLECTOR_OTLP_ENABLED=true \
        -p 6831:6831/udp -p 6832:6832/udp \
        -p 5778:5778 -p 16686:16686 -p 4317:4317 -p 4318:4318 -p 14250:14250  -p 14268:14268 -p 14269:14269 -p 9411:9411 \
        quay.io/jaegertracing/all-in-one:latest
        ```
    2. While the server is running, browse to http://localhost:16686 to view tracing events.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell
./mvnw clean compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev-ui.

## Packaging and running the application locally

The application can be packaged using:
```shell
./mvnw clean package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using:
```shell
java -Dquarkus.kubernetes-config.enabled=false -jar target/quarkus-app/quarkus-run.jar
```

If you want to build an _über-jar_, execute the following command:
```shell
./mvnw clean package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using:
```shell
java -Dquarkus.kubernetes-config.enabled=false -jar target/books-api-v2-2.0.0-runner.jar
```

According to your environment, you may want to customize the Jaeger collector endpoint by adding the following run-time _system properties_:
- `quarkus.otel.exporter.otlp.endpoint`

Example:
```
java -Dquarkus.kubernetes-config.enabled=false -Dquarkus.otel.exporter.otlp.endpoint="http://localhost:4317" -jar target/quarkus-app/quarkus-run.jar
```

## Packaging and running the application on Red Hat OpenShift

### Pre-requisites
- Access to a [Red Hat OpenShift](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster
- User has self-provisioner privilege or has access to a working OpenShift project

1. Login to the OpenShift cluster
    ```shell
    oc login ...
    ```

2. Create an OpenShift project or use your existing OpenShift project. For instance, to create `ceq-services-jvm`
    ```shell
    oc new-project ceq-services-jvm --display-name="Red Hat build of Apache Camel for Quarkus Apps - JVM Mode"
    ```

3. Create an `allInOne` Jaeger instance.
    1. **IF NOT ALREADY INSTALLED**:
        1. Install, via OLM, the `Red Hat OpenShift distributed tracing platform` (Jaeger) operator with an `AllNamespaces` scope. :warning: Needs `cluster-admin` privileges
            ```shell
            oc create --save-config -f - <<EOF
            apiVersion: operators.coreos.com/v1alpha1
            kind: Subscription
            metadata:
                name: jaeger-product
                namespace: openshift-operators
            spec:
                channel: stable
                installPlanApproval: Automatic
                name: jaeger-product
                source: redhat-operators
                sourceNamespace: openshift-marketplace
            EOF
            ```
        2. Verify the successful installation of the `Red Hat OpenShift distributed tracing platform` operator
            ```shell
            watch oc get sub,csv
            ```
    2. Create the `allInOne` Jaeger instance.
        ```shell
        oc create --save-config -f - <<EOF
        apiVersion: jaegertracing.io/v1
        kind: Jaeger
        metadata:
            name: jaeger-all-in-one-inmemory
        spec:
            allInOne:
                options:
                log-level: info
            strategy: allInOne
        EOF
        ```

4. Deploy to OpenShift using the _**S2I binary workflow**_
    ```shell
    ./mvnw clean package -Dquarkus.openshift.deploy=true
    ```

## Testing the application on OpenShift

### Pre-requisites

- [**`curl`**](https://curl.se/) or [**`HTTPie`**](https://httpie.io/) command line tools. 
- [**`HTTPie`**](https://httpie.io/) has been used in the tests.

### Testing instructions:

1. Get the OpenShift route hostname
    ```shell
    URL="https://$(oc get route books-api-v2 -o jsonpath='{.spec.host}')"
    ```
    
2. Test the `/api/v2/books` endpoint

    - `GET` method:
        ```shell
        http $URL/api/v2/books
        ```
        ```shell
        [...]
        HTTP/1.1 200 OK
        [...]
        [
            {
                "author": {
                    "birthDate": "1797-08-30T00:00:00.000Z",
                    "name": "Mary Shelley"
                },
                "copies": 10,
                "title": "Frankenstein",
                "year": 1818
            },
            {
                "author": {
                    "birthDate": "1812-02-07T00:00:00.000Z",
                    "name": "Charles Dickens"
                },
                "copies": 5,
                "title": "A Christmas Carol",
                "year": 1843
            },
            {
                "author": {
                    "birthDate": "1775-12-16T00:00:00.000Z",
                    "name": "Jane Austen"
                },
                "copies": 3,
                "title": "Pride and Prejudice",
                "year": 1813
            }
        ]
        ```
    
    - `POST` method (_valid payload_):
        ```shell
        echo '{
            "author": {
                "birthDate": "1642-12-25T00:00:00.000Z",
                "name": "Sir Isaac Newton"
            },
            "copies": 31,
            "title": "Philosophiæ Naturalis Principia Mathematica",
            "year": 1687
        }' | http POST $URL/api/v2/books 'Content-Type: application/json'
        ```
        ```shell
        [...]
        HTTP/1.1 201 Created
        [...]
        [
            {
                "author": {
                    "birthDate": "1797-08-30T00:00:00.000Z",
                    "name": "Mary Shelley"
                },
                "copies": 10,
                "title": "Frankenstein",
                "year": 1818
            },
            {
                "author": {
                    "birthDate": "1812-02-07T00:00:00.000Z",
                    "name": "Charles Dickens"
                },
                "copies": 5,
                "title": "A Christmas Carol",
                "year": 1843
            },
            {
                "author": {
                    "birthDate": "1775-12-16T00:00:00.000Z",
                    "name": "Jane Austen"
                },
                "copies": 3,
                "title": "Pride and Prejudice",
                "year": 1813
            },
            {
                "author": {
                    "birthDate": "1642-12-25T00:00:00.000Z",
                    "name": "Sir Isaac Newton"
                },
                "copies": 31,
                "title": "Philosophiæ Naturalis Principia Mathematica",
                "year": 1687
            }
        ]
        ```

    - `POST` method (_invalid payload_):
        ```shell
        echo '{
            "authorName": "Sir Isaac Newton",
            "copies": 31,
            "title": "Philosophiæ Naturalis Principia Mathematica",
            "year": 1687
        }' | http POST $URL/api/v2/books 'Content-Type: application/json'
        ```
        ```shell
        [...]
        HTTP/1.1 400 Bad Request
        [...]
        {
            "error": {
                "description": "Bad Request",
                "id": "400",
                "messages": [
                    "JSON validation error with 2 errors:\n$: required property 'author' not found\n$: property 'authorName' is not defined in the schema and the schema does not allow additional properties. Exchange[94595332629B781-0000000000000002]"
                ]
            }
        }
        ```

## Testing using [Postman](https://www.postman.com/)

Import the provided Postman Collection for testing: [tests/Books-Api-v2.postman_collection.json](./tests/Books-Api-v2.postman_collection.json)
 
![Books-Api-v2.postman_collection.png](../_images/Books-Api-v2.postman_collection.png)

## Creating a native executable

You can create a native executable using the following command:

```shell
./mvnw clean package -Pnative -Dquarkus.native.native-image-xmx=7g
```

>**NOTE** : The project is configured to use a container runtime for native builds. See `quarkus.native.container-build=true` in the [`application.yml`](./src/main/resources/application.yml). Also, adjust the `quarkus.native.native-image-xmx` value according to your container runtime available memory resources.

You can then execute your native executable with: `./target/books-api-v2-2.0.0-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image.

>**NOTE** : If your are on Apple Silicon and built the native image inside a Linux container (-Dquarkus.native.container-build=true), the result is a Linux ELF binary. macOS can’t execute Linux binaries, so launching it on macOS yields “exec format error”. Follow the steps below to run your Linux native binary.

1. Build the container image of your Linux native binary:
    ```shell
    podman build -f src/main/docker/Dockerfile.native -t books-api-v2 .
    ```
2. Run the container:
    ```shell
    podman run --rm --name books-api-v2 \
    -p 8080:8080,9876:9876 \
    -e QUARKUS_KUBERNETES-CONFIG_ENABLED=false \
    -e QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://host.containers.internal:4317 \
    books-api-v2 
    ```

## Start-up time comparison in the same environment

Used environment:
- **Laptop**: MacBook PRO
- **CPU**: Apple M2 PRO
- **RAM**: 32Gb
- **Container runtime for native builds**: podman v5.7.0

### JVM mode -> _started in **1.734s**_

```shell
# java -Dquarkus.kubernetes-config.enabled=false -jar target/quarkus-app/quarkus-run.jar
[...]
2025-12-02 12:23:41,640 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.MainSupport] (main) Apache Camel (Main) 4.14.0.redhat-00009 is starting
2025-12-02 12:23:41,665 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main) Auto-configuration summary
2025-12-02 12:23:41,666 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main)     [MicroProfilePropertiesSource] camel.context.name = books-api-v2
2025-12-02 12:23:41,838 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Apache Camel 4.14.0.redhat-00009 (books-api-v2) is starting
2025-12-02 12:23:41,893 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.op.OpenTelemetryTracer] (main) OpenTelemetryTracer enabled using instrumentation-name: camel
2025-12-02 12:23:41,894 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Using ThreadPoolFactory: org.apache.camel.opentelemetry.OpenTelemetryInstrumentedThreadPoolFactory@f3fcd59
2025-12-02 12:23:41,961 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main) Property-placeholders summary
2025-12-02 12:23:41,961 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main)     [MicroProfilePropertiesSource] deployment.location = OpenShift
2025-12-02 12:23:41,962 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Routes startup (total:5 rest-dsl:1)
2025-12-02 12:23:41,962 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started common-500-http-code-route (direct://common-500)
2025-12-02 12:23:41,962 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started custom-http-error-route (direct://custom-http-error)
2025-12-02 12:23:41,963 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started getBooks-v2 (direct://getBooks-v2)
2025-12-02 12:23:41,963 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started addNewBook-v2 (direct://addNewBook-v2)
2025-12-02 12:23:41,963 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started route1 (rest-openapi://classpath:META-INF/openapi.yaml)
2025-12-02 12:23:41,963 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Apache Camel 4.14.0.redhat-00009 (books-api-v2) started in 124ms (build:0ms init:0ms start:124ms boot:1s193ms)
2025-12-02 12:23:41,998 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) books-api-v2 2.0.0 on JVM (powered by Quarkus 3.27.0.redhat-00001) started in 1.734s. Listening on: http://0.0.0.0:8080. Management interface listening on http://0.0.0.0:9876.
2025-12-02 12:23:41,999 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) Profile prod activated. 
2025-12-02 12:23:41,999 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) Installed features: [camel-attachments, camel-core, camel-direct, camel-jackson, camel-jolokia, camel-json-validator, camel-log, camel-management, camel-micrometer, camel-microprofile-health, camel-observability-services, camel-openapi-java, camel-opentelemetry, camel-platform-http, camel-rest, camel-rest-openapi, camel-xml-io-dsl, cdi, config-yaml, kubernetes, kubernetes-client, micrometer, opentelemetry, rest, smallrye-context-propagation, smallrye-health, smallrye-openapi, swagger-ui, vertx]
```

### Native mode -> _started in **0.266s**_

```shell
# podman run --rm --name books-api-v2 -p 8080:8080,9876:9876 -e QUARKUS_KUBERNETES-CONFIG_ENABLED=false -e QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://host.containers.internal:4317 books-api-v2
[...]
2025-12-02 11:24:42,195 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Apache Camel 4.14.0.redhat-00009 (books-api-v2) is starting
2025-12-02 11:24:42,247 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.op.OpenTelemetryTracer] (main) OpenTelemetryTracer enabled using instrumentation-name: camel
2025-12-02 11:24:42,248 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Using ThreadPoolFactory: org.apache.camel.opentelemetry.OpenTelemetryInstrumentedThreadPoolFactory@9c40f43
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main) Property-placeholders summary
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main)     [MicroProfilePropertiesSource] deployment.location = OpenShift
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Routes startup (total:5 rest-dsl:1)
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started common-500-http-code-route (direct://common-500)
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started custom-http-error-route (direct://custom-http-error)
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started getBooks-v2 (direct://getBooks-v2)
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started addNewBook-v2 (direct://addNewBook-v2)
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started route1 (rest-openapi://classpath:META-INF/openapi.yaml)
2025-12-02 11:24:42,295 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Apache Camel 4.14.0.redhat-00009 (books-api-v2) started in 99ms (build:0ms init:0ms start:99ms)
2025-12-02 11:24:42,299 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) books-api-v2 2.0.0 native (powered by Quarkus 3.27.0.redhat-00001) started in 0.266s. Listening on: http://0.0.0.0:8080. Management interface listening on http://0.0.0.0:9876.
2025-12-02 11:24:42,299 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) Profile prod activated. 
2025-12-02 11:24:42,299 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) Installed features: [camel-attachments, camel-core, camel-direct, camel-jackson, camel-jolokia, camel-json-validator, camel-log, camel-management, camel-micrometer, camel-microprofile-health, camel-observability-services, camel-openapi-java, camel-opentelemetry, camel-platform-http, camel-rest, camel-rest-openapi, camel-xml-io-dsl, cdi, config-yaml, kubernetes, kubernetes-client, micrometer, opentelemetry, rest, smallrye-context-propagation, smallrye-health, smallrye-openapi, swagger-ui, vertx]
```