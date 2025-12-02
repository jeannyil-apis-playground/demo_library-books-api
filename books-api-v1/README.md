# books-api-v1

This project leverages [**Red Hat build of Quarkus 3.27.x**](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.27), the Supersonic Subatomic Java Framework. More specifically, the project is implemented using [**Red Hat build of Apache Camel v4.14.x for Quarkus**](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14#Red%20Hat%20build%20of%20Apache%20Camel%20for%20Quarkus).

This project implements a simple REST API that manages books. The following endpoints are exposed:
- `/api/v1/books` : 
    - `GET` method returns a list of all `Books-v1` entities.
    - `POST` method adds a new `book-v1` entity in the inventory.
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
java -Dquarkus.kubernetes-config.enabled=false -jar target/books-api-v1-1.0.0-runner.jar
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
    URL="https://$(oc get route books-api-v1 -o jsonpath='{.spec.host}')"
    ```
    
2. Test the `/api/v1/books` endpoint

    - `GET` method:
        ```shell
        http $URL/api/v1/books
        ```
        ```console
        [...]
        HTTP/1.1 200 OK
        [...]
        [
            {
                "authorName": "Mary Shelley",
                "copies": 10,
                "title": "Frankenstein",
                "year": 1818
            },
            {
                "authorName": "Charles Dickens",
                "copies": 5,
                "title": "A Christmas Carol",
                "year": 1843
            },
            {
                "authorName": "Jane Austen",
                "copies": 3,
                "title": "Pride and Prejudice",
                "year": 1813
            }
        ]
        ```

    - `POST` method:
        ```shell
        echo '{                                   
            "authorName": "Sir Isaac Newton",
            "copies": 31,
            "title": "Philosophiæ Naturalis Principia Mathematica",
            "year": 1687
        }' | http POST $URL/api/v1/books 'Content-Type: application/json'
        ```
        ```console
        [...]
        HTTP/1.1 201 Created
        [...]
        [
            {
                "authorName": "Mary Shelley",
                "copies": 10,
                "title": "Frankenstein",
                "year": 1818
            },
            {
                "authorName": "Charles Dickens",
                "copies": 5,
                "title": "A Christmas Carol",
                "year": 1843
            },
            {
                "authorName": "Jane Austen",
                "copies": 3,
                "title": "Pride and Prejudice",
                "year": 1813
            },
            {
                "authorName": "Sir Isaac Newton",
                "copies": 31,
                "title": "Philosophiæ Naturalis Principia Mathematica",
                "year": 1687
            }
        ]
        ```

## Testing using [Postman](https://www.postman.com/)

Import the provided Postman Collection for testing: [tests/Books-Api-v1.postman_collection.json](./tests/Books-Api-v1.postman_collection.json)
 
![Books-Api-v1.postman_collection.png](../_images/Books-Api-v1.postman_collection.png)

## Creating a native executable

You can create a native executable using the following command:

```shell
./mvnw clean package -Pnative -Dquarkus.native.native-image-xmx=7g
```

>**NOTE** : The project is configured to use a container runtime for native builds. See `quarkus.native.container-build=true` in the [`application.yml`](./src/main/resources/application.yml). Also, adjust the `quarkus.native.native-image-xmx` value according to your container runtime available memory resources.

You can then execute your native executable with: `./target/books-api-v1-1.0.0-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image.

>**NOTE** : If your are on Apple Silicon and built the native image inside a Linux container (-Dquarkus.native.container-build=true), the result is a Linux ELF binary. macOS can’t execute Linux binaries, so launching it on macOS yields “exec format error”. Follow the steps below to run your Linux native binary.

1. Build the container image of your Linux native binary:
    ```shell
    podman build -f src/main/docker/Dockerfile.native -t books-api-v1 .
    ```
2. Run the container:
    ```shell
    podman run --rm --name books-api-v1 \
    -p 8080:8080,9876:9876 \
    -e QUARKUS_KUBERNETES-CONFIG_ENABLED=false \
    -e QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://host.containers.internal:4317 \
    books-api-v1 
    ```

## Start-up time comparison in the same environment

Used environment:
- **Laptop**: MacBook PRO
- **CPU**: Apple M2 PRO
- **RAM**: 32Gb
- **Container runtime for native builds**: podman v5.7.0

### JVM mode -> _started in **1.507s**_

```shell
# java -Dquarkus.kubernetes-config.enabled=false -jar target/quarkus-app/quarkus-run.jar
[...]
2025-12-02 11:25:09,692 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.MainSupport] (main) Apache Camel (Main) 4.14.0.redhat-00009 is starting
2025-12-02 11:25:09,744 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main) Auto-configuration summary
2025-12-02 11:25:09,744 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main)     [MicroProfilePropertiesSource] camel.context.name = books-api-v1
2025-12-02 11:25:09,883 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Apache Camel 4.14.0.redhat-00009 (books-api-v1) is starting
2025-12-02 11:25:09,934 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.op.OpenTelemetryTracer] (main) OpenTelemetryTracer enabled using instrumentation-name: camel
2025-12-02 11:25:09,935 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Using ThreadPoolFactory: org.apache.camel.opentelemetry.OpenTelemetryInstrumentedThreadPoolFactory@4e61e4c2
2025-12-02 11:25:09,976 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main) Property-placeholders summary
2025-12-02 11:25:09,977 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main)     [MicroProfilePropertiesSource] deployment.location = OpenShift
2025-12-02 11:25:09,977 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Routes startup (total:3 rest-dsl:1)
2025-12-02 11:25:09,978 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started getBooks-v1 (direct://getBooks-v1)
2025-12-02 11:25:09,978 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started addNewBook-v1 (direct://addNewBook-v1)
2025-12-02 11:25:09,978 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started route1 (rest-openapi://classpath:META-INF/openapi.yaml)
2025-12-02 11:25:09,978 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Apache Camel 4.14.0.redhat-00009 (books-api-v1) started in 94ms (build:0ms init:0ms start:94ms boot:1s65ms)
2025-12-02 11:25:10,011 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) books-api-v1 1.0.0 on JVM (powered by Quarkus 3.27.0.redhat-00001) started in 1.507s. Listening on: http://0.0.0.0:8080. Management interface listening on http://0.0.0.0:9876.
2025-12-02 11:25:10,011 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) Profile prod activated. 
2025-12-02 11:25:10,011 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) Installed features: [camel-attachments, camel-core, camel-direct, camel-jackson, camel-jolokia, camel-log, camel-management, camel-micrometer, camel-microprofile-health, camel-observability-services, camel-openapi-java, camel-opentelemetry, camel-platform-http, camel-rest, camel-rest-openapi, camel-xml-io-dsl, cdi, config-yaml, kubernetes, kubernetes-client, micrometer, opentelemetry, rest, smallrye-context-propagation, smallrye-health, smallrye-openapi, swagger-ui, vertx]
```

### Native mode -> _started in **0.150s**_

```shell
# podman run --rm --name books-api-v1 -p 8080:8080,9876:9876 -e QUARKUS_KUBERNETES-CONFIG_ENABLED=false -e QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://host.containers.internal:4317 books-api-v1
[...]
2025-12-02 10:28:19,863 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.MainSupport] (main) Apache Camel (Main) 4.14.0.redhat-00009 is starting
2025-12-02 10:28:19,869 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main) Auto-configuration summary
2025-12-02 10:28:19,869 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main)     [MicroProfilePropertiesSource] camel.context.name = books-api-v1
2025-12-02 10:28:19,900 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Apache Camel 4.14.0.redhat-00009 (books-api-v1) is starting
2025-12-02 10:28:19,923 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.op.OpenTelemetryTracer] (main) OpenTelemetryTracer enabled using instrumentation-name: camel
2025-12-02 10:28:19,924 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Using ThreadPoolFactory: org.apache.camel.opentelemetry.OpenTelemetryInstrumentedThreadPoolFactory@49bc1f6c
2025-12-02 10:28:19,957 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main) Property-placeholders summary
2025-12-02 10:28:19,957 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.ma.BaseMainSupport] (main)     [MicroProfilePropertiesSource] deployment.location = OpenShift
2025-12-02 10:28:19,958 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Routes startup (total:3 rest-dsl:1)
2025-12-02 10:28:19,958 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started getBooks-v1 (direct://getBooks-v1)
2025-12-02 10:28:19,958 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started addNewBook-v1 (direct://addNewBook-v1)
2025-12-02 10:28:19,958 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main)     Started route1 (rest-openapi://classpath:META-INF/openapi.yaml)
2025-12-02 10:28:19,958 INFO  traceId=, parentId=, spanId=, sampled= [or.ap.ca.im.en.AbstractCamelContext] (main) Apache Camel 4.14.0.redhat-00009 (books-api-v1) started in 57ms (build:0ms init:0ms start:57ms)
2025-12-02 10:28:19,960 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) books-api-v1 1.0.0 native (powered by Quarkus 3.27.0.redhat-00001) started in 0.150s. Listening on: http://0.0.0.0:8080. Management interface listening on http://0.0.0.0:9876.
2025-12-02 10:28:19,960 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) Profile prod activated. 
2025-12-02 10:28:19,960 INFO  traceId=, parentId=, spanId=, sampled= [io.quarkus] (main) Installed features: [camel-attachments, camel-core, camel-direct, camel-jackson, camel-jolokia, camel-log, camel-management, camel-micrometer, camel-microprofile-health, camel-observability-services, camel-openapi-java, camel-opentelemetry, camel-platform-http, camel-rest, camel-rest-openapi, camel-xml-io-dsl, cdi, config-yaml, kubernetes, kubernetes-client, micrometer, opentelemetry, rest, smallrye-context-propagation, smallrye-health, smallrye-openapi, swagger-ui, vertx]
```