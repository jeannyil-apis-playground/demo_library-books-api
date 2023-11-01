# books-api-v1

This project leverages **Red Hat build of Quarkus 2.13.x**, the Supersonic Subatomic Java Framework. More specifically, the project is implemented using [**Red Hat Camel Extensions for Quarkus (RHCEQ) 2.13.x**](https://access.redhat.com/documentation/en-us/red_hat_integration/2023.q1/html/getting_started_with_camel_extensions_for_quarkus/index).

This project implements a simple REST API that returns a list of books. The following endpoints are exposed:
- `/api/v1/books` : returns a list of all `Books-v1` entities.
- `/api/v1/openapi.json`: returns the OpenAPI 3.0 specification for the service.
- `/q/health` : returns the _Camel Quarkus MicroProfile_ health checks
- `/q/metrics` : the _Camel Quarkus Micrometer_ metrics in prometheus format

## Prerequisites

- Maven 3.8.1+
- JDK 17 installed with `JAVA_HOME` configured appropriately
- A running [_Red Hat OpenShift 4_](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/books-api-v1-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Deploy the RHCEQ application to OpenShift

### Instructions

1. Login to the OpenShift cluster
    ```script shell
    oc login ...
    ```

2. Create an OpenShift project to host the service
    ```script shell
    oc new-project ceq-services-jvm --display-name="Red Hat Camel Extensions for Quarkus Apps - JVM Mode"
    ```

3. Package and deploy the RHCEQ service to OpenShift
    ```script shell
    ./mvnw clean package -Dquarkus.kubernetes.deploy=true -Dquarkus.container-image.group=ceq-services-jvm
    ```

### OpenTelemetry with Jaeger

[**Jaeger**](https://www.jaegertracing.io/), a distributed tracing system for observability ([_open tracing_](https://opentracing.io/)). 

#### Running Jaeger locally

:bulb: A simple way of starting a Jaeger tracing server is with `docker` or `podman`:

1. Start the Jaeger tracing server:
    ```
    podman run --rm -e COLLECTOR_ZIPKIN_HOST_PORT=:9411 -e COLLECTOR_OTLP_ENABLED=true \
    -p 6831:6831/udp -p 6832:6832/udp \
    -p 5778:5778 -p 16686:16686 -p 4317:4317 -p 4318:4318 -p 14250:14250  -p 14268:14268 -p 14269:14269 -p 9411:9411 \
    quay.io/jaegertracing/all-in-one:latest
    ```
2. While the server is running, browse to http://localhost:16686 to view tracing events.

#### Deploying Jaeger on OpenShift

1. If not already installed, install the Red Hat OpenShift distributed tracing platform (Jaeger) operator with an AllNamespaces scope.
_**:warning: cluster-admin privileges are required**_
    ```
    oc apply -f - <<EOF
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

2. Verify the successful installation of the Red Hat OpenShift distributed tracing platform operator
    ```script shell
    watch oc get sub,csv
    ```

3. Create the allInOne Jaeger instance in the dsna-pilot OpenShift project
    ```script shell
    oc apply -f - <<EOF
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

## :bulb: Testing the application on OpenShift

### Pre-requisites

- [**`curl`**](https://curl.se/) or [**`HTTPie`**](https://httpie.io/) command line tools. 
- [**`HTTPie`**](https://httpie.io/) has been used in the tests.

### Testing instructions:

1. Get the OpenShift route hostname
    ```shell script
    URL="http://$(oc get route books-api-v1 -o jsonpath='{.spec.host}')"
    ```
    
2. Test the `/api/v1/books` endpoint

    - `GET` method:
        ```shell script
        http $URL/api/v1/books
        ```
        ```console
        [...]
        HTTP/1.1 200 OK
        [...]
        Content-Type: application/json
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
        ```shell script
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
        Content-Type: application/json
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

3. Test the `/api/v1/openapi.json` endpoint
    ```shell script
    http $URL/api/v1/openapi.json
    ```
    ```console
    [...]
    HTTP/1.1 200 OK
    [...]
    {
        "components": {
            "schemas": {
                "book-v1": {
                    "description": "A Book (v1) entity",
                    "example": {
                        "authorName": "Mary Shelley",
                        "copies": 10,
                        "title": "Frankenstein",
                        "year": 1818
                    },
                    "properties": {
                        "authorName": {
                            "type": "string"
                        },
                        "copies": {
                            "format": "int32",
                            "type": "integer"
                        },
                        "title": {
                            "type": "string"
                        },
                        "year": {
                            "format": "int32",
                            "type": "integer"
                        }
                    },
                    "title": "Root Type for book-v1",
                    "type": "object"
                }
            }
        },
        "info": {
            "description": "Manages a library books inventory",
            "title": "Library Books API (v1)",
            "version": "1.0.0"
        },
        "openapi": "3.0.2",
        "paths": {
            "/books": {
                "description": "The REST endpoint/path used to list and create zero or more `books-v1` entities.  This path contains a `GET` operation to perform the list tasks.",
                "get": {
                    "description": "Gets a list of all `book-v1` entities from the inventory.",
                    "operationId": "getBooks-v1",
                    "responses": {
                        "200": {
                            "content": {
                                "application/json": {
                                    "examples": {
                                        "ListOfBooks-v1": {
                                            "value": [
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
                                        }
                                    },
                                    "schema": {
                                        "items": {
                                            "$ref": "#/components/schemas/book-v1"
                                        },
                                        "type": "array"
                                    }
                                }
                            },
                            "description": "Successful response - returns an array of `books-v1` entities."
                        }
                    },
                    "summary": "List all books (v1) from the inventory",
                    "tags": [
                        "Books"
                    ]
                },
                "post": {
                    "description": "Adds a new `book-v1` entity in the inventory.",
                    "operationId": "addNewBook-v1",
                    "requestBody": {
                        "content": {
                            "application/json": {
                                "examples": {
                                    "NewBook-v1": {
                                        "value": {
                                            "authorName": "Sir Isaac Newton",
                                            "copies": 31,
                                            "title": "Philosophiæ Naturalis Principia Mathematica",
                                            "year": 1687
                                        }
                                    }
                                },
                                "schema": {
                                    "$ref": "#/components/schemas/book-v1"
                                }
                            }
                        },
                        "required": true
                    },
                    "responses": {
                        "201": {
                            "content": {
                                "application/json": {
                                    "examples": {
                                        "NewListOfBooks-v1": {
                                            "value": [
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
                                                    "authorName": "Isaac Newton",
                                                    "copies": 31,
                                                    "title": "Philosophiæ Naturalis Principia Mathematica",
                                                    "year": 1687
                                                }
                                            ]
                                        }
                                    },
                                    "schema": {
                                        "items": {
                                            "$ref": "#/components/schemas/book-v1"
                                        },
                                        "type": "array"
                                    }
                                }
                            },
                            "description": "Added"
                        }
                    },
                    "summary": "Adds a new book (v1) in the inventory",
                    "tags": [
                        "Books"
                    ]
                },
                "summary": "Path used to manage the list of books-v1."
            }
        },
        "servers": [
            {
                "description": "Server URL",
                "url": "http://books-api-v1.ceq-services-jvm.svc.cluster.local/api/v1"
            }
        ],
        "tags": [
            {
                "description": "",
                "name": "Books"
            }
        ]
    }
    ```

4. Test the `/q/health` endpoint
    ```shell script
    http $URL/q/health
    ```
    ```console
    HTTP/1.1 200 OK
    cache-control: private
    content-length: 525
    content-type: application/json; charset=UTF-8
    set-cookie: 9dcf4baeb4aaf2796beba21cfecc7c84=853ce15fc7754dc9027d0d018ffe2f0a; path=/; HttpOnly

    {
        "checks": [
            {
                "name": "camel-routes",
                "status": "UP"
            },
            {
                "data": {
                    "check.kind": "READINESS",
                    "context.name": "books-api-v1",
                    "context.status": "Started",
                    "context.version": "3.18.6.redhat-00007"
                },
                "name": "context",
                "status": "UP"
            },
            {
                "name": "camel-consumers",
                "status": "UP"
            }
        ],
        "status": "UP"
    }
    ```

5. Test the `/q/metrics` endpoint
    ```shell script
    http $URL/q/metrics
    ```
    ```console
    [...]
    HTTP/1.1 200 OK
    cache-control: private
    content-length: 25592
    content-type: text/plain; version=0.0.4; charset=utf-8
    set-cookie: 9dcf4baeb4aaf2796beba21cfecc7c84=e065ba80d6700dc0ff47085945332d6f; path=/; HttpOnly

    # HELP worker_pool_idle The number of resources from the pool currently used
    # TYPE worker_pool_idle gauge
    worker_pool_idle{pool_name="vert.x-internal-blocking",pool_type="worker",} 20.0
    worker_pool_idle{pool_name="vert.x-worker-thread",pool_type="worker",} 19.0
    # HELP jvm_memory_usage_after_gc_percent The percentage of long-lived heap pool used after the last GC event, in the range [0..1]
    # TYPE jvm_memory_usage_after_gc_percent gauge
    jvm_memory_usage_after_gc_percent{area="heap",pool="long-lived",} 0.08373851429332387
    # HELP process_cpu_usage The "recent cpu usage" for the Java Virtual Machine process
    # TYPE process_cpu_usage gauge
    process_cpu_usage 0.3128526645768025
    # HELP CamelExchangesTotal_total  
    # TYPE CamelExchangesTotal_total counter
    CamelExchangesTotal_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService",} 6.0
    CamelExchangesTotal_total{camelContext="books-api-v1",routeId="addNewBook-v1",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelExchangesTotal_total{camelContext="books-api-v1",routeId="add-new-book-v1",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelExchangesTotal_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelExchangesTotal_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelExchangesTotal_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService",} 6.0
    # HELP worker_pool_queue_delay_seconds_max Time spent in the waiting queue before being processed
    # TYPE worker_pool_queue_delay_seconds_max gauge
    worker_pool_queue_delay_seconds_max{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_queue_delay_seconds_max{pool_name="vert.x-worker-thread",pool_type="worker",} 9.804E-5
    # HELP worker_pool_queue_delay_seconds Time spent in the waiting queue before being processed
    # TYPE worker_pool_queue_delay_seconds summary
    worker_pool_queue_delay_seconds_count{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_queue_delay_seconds_sum{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_queue_delay_seconds_count{pool_name="vert.x-worker-thread",pool_type="worker",} 25.0
    worker_pool_queue_delay_seconds_sum{pool_name="vert.x-worker-thread",pool_type="worker",} 0.004877038
    # HELP jvm_memory_committed_bytes The amount of memory in bytes that is committed for the Java virtual machine to use
    # TYPE jvm_memory_committed_bytes gauge
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'",} 7340032.0
    jvm_memory_committed_bytes{area="heap",id="PS Old Gen",} 1.3631488E7
    jvm_memory_committed_bytes{area="heap",id="PS Survivor Space",} 2097152.0
    jvm_memory_committed_bytes{area="heap",id="PS Eden Space",} 3145728.0
    jvm_memory_committed_bytes{area="nonheap",id="Metaspace",} 4.554752E7
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'non-nmethods'",} 2555904.0
    jvm_memory_committed_bytes{area="nonheap",id="Compressed Class Space",} 6160384.0
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'",} 2555904.0
    # HELP CamelExchangesFailed_total  
    # TYPE CamelExchangesFailed_total counter
    CamelExchangesFailed_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v1",routeId="addNewBook-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v1",routeId="add-new-book-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService",} 0.0
    # HELP jvm_threads_peak_threads The peak live thread count since the Java virtual machine started or peak was reset
    # TYPE jvm_threads_peak_threads gauge
    jvm_threads_peak_threads 22.0
    # HELP jvm_gc_memory_promoted_bytes_total Count of positive increases in the size of the old generation memory pool before GC to after GC
    # TYPE jvm_gc_memory_promoted_bytes_total counter
    jvm_gc_memory_promoted_bytes_total 4673704.0
    # HELP jvm_buffer_total_capacity_bytes An estimate of the total capacity of the buffers in this pool
    # TYPE jvm_buffer_total_capacity_bytes gauge
    jvm_buffer_total_capacity_bytes{id="mapped - 'non-volatile memory'",} 0.0
    jvm_buffer_total_capacity_bytes{id="mapped",} 0.0
    jvm_buffer_total_capacity_bytes{id="direct",} 434207.0
    # HELP process_start_time_seconds Start time of the process since unix epoch.
    # TYPE process_start_time_seconds gauge
    process_start_time_seconds 1.698847805708E9
    # HELP CamelExchangesInflight  
    # TYPE CamelExchangesInflight gauge
    CamelExchangesInflight{camelContext="books-api-v1",routeId="add-new-book-v1",serviceName="MicrometerEventNotifierService",} 0.0
    CamelExchangesInflight{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerEventNotifierService",} 0.0
    CamelExchangesInflight{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerEventNotifierService",} 0.0
    # HELP worker_pool_completed_total Number of times resources from the pool have been acquired
    # TYPE worker_pool_completed_total counter
    worker_pool_completed_total{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_completed_total{pool_name="vert.x-worker-thread",pool_type="worker",} 24.0
    # HELP http_server_bytes_written_max Number of bytes sent by the server
    # TYPE http_server_bytes_written_max gauge
    http_server_bytes_written_max 4096.0
    # HELP http_server_bytes_written Number of bytes sent by the server
    # TYPE http_server_bytes_written summary
    http_server_bytes_written_count 25.0
    http_server_bytes_written_sum 15540.0
    # HELP CamelRoutePolicy_seconds_max  
    # TYPE CamelRoutePolicy_seconds_max gauge
    CamelRoutePolicy_seconds_max{camelContext="books-api-v1",failed="false",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService",} 0.100501252
    CamelRoutePolicy_seconds_max{camelContext="books-api-v1",failed="false",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService",} 0.595036886
    CamelRoutePolicy_seconds_max{camelContext="books-api-v1",failed="false",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.001652271
    CamelRoutePolicy_seconds_max{camelContext="books-api-v1",failed="false",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.002905851
    CamelRoutePolicy_seconds_max{camelContext="books-api-v1",failed="false",routeId="addNewBook-v1",serviceName="MicrometerRoutePolicyService",} 0.005072773
    CamelRoutePolicy_seconds_max{camelContext="books-api-v1",failed="false",routeId="add-new-book-v1",serviceName="MicrometerRoutePolicyService",} 0.009044016
    # HELP CamelRoutePolicy_seconds  
    # TYPE CamelRoutePolicy_seconds summary
    CamelRoutePolicy_seconds_count{camelContext="books-api-v1",failed="false",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService",} 6.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v1",failed="false",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService",} 0.111695902
    CamelRoutePolicy_seconds_count{camelContext="books-api-v1",failed="false",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService",} 6.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v1",failed="false",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService",} 0.612235491
    CamelRoutePolicy_seconds_count{camelContext="books-api-v1",failed="false",routeId="route1",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v1",failed="false",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.001652271
    CamelRoutePolicy_seconds_count{camelContext="books-api-v1",failed="false",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v1",failed="false",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.002905851
    CamelRoutePolicy_seconds_count{camelContext="books-api-v1",failed="false",routeId="addNewBook-v1",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v1",failed="false",routeId="addNewBook-v1",serviceName="MicrometerRoutePolicyService",} 0.006907037
    CamelRoutePolicy_seconds_count{camelContext="books-api-v1",failed="false",routeId="add-new-book-v1",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v1",failed="false",routeId="add-new-book-v1",serviceName="MicrometerRoutePolicyService",} 0.011869844
    # HELP worker_pool_active The number of resources from the pool currently used
    # TYPE worker_pool_active gauge
    worker_pool_active{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_active{pool_name="vert.x-worker-thread",pool_type="worker",} 1.0
    # HELP CamelRoutesAdded_routes  
    # TYPE CamelRoutesAdded_routes gauge
    CamelRoutesAdded_routes{camelContext="books-api-v1",eventType="RouteEvent",serviceName="MicrometerEventNotifierService",} 6.0
    # HELP http_server_bytes_read Number of bytes received by the server
    # TYPE http_server_bytes_read summary
    http_server_bytes_read_count 2.0
    http_server_bytes_read_sum 340.0
    # HELP http_server_bytes_read_max Number of bytes received by the server
    # TYPE http_server_bytes_read_max gauge
    http_server_bytes_read_max 173.0
    # HELP jvm_buffer_count_buffers An estimate of the number of buffers in the pool
    # TYPE jvm_buffer_count_buffers gauge
    jvm_buffer_count_buffers{id="mapped - 'non-volatile memory'",} 0.0
    jvm_buffer_count_buffers{id="mapped",} 0.0
    jvm_buffer_count_buffers{id="direct",} 13.0
    # HELP system_load_average_1m The sum of the number of runnable entities queued to available processors and the number of runnable entities running on the available processors averaged over a period of time
    # TYPE system_load_average_1m gauge
    system_load_average_1m 0.38
    # HELP http_server_connections_seconds_max The duration of the connections
    # TYPE http_server_connections_seconds_max gauge
    http_server_connections_seconds_max 0.010311117
    # HELP http_server_connections_seconds The duration of the connections
    # TYPE http_server_connections_seconds summary
    http_server_connections_seconds_active_count 1.0
    http_server_connections_seconds_duration_sum 0.009580903
    # HELP jvm_gc_pause_seconds Time spent in GC pause
    # TYPE jvm_gc_pause_seconds summary
    jvm_gc_pause_seconds_count{action="end of major GC",cause="Ergonomics",} 1.0
    jvm_gc_pause_seconds_sum{action="end of major GC",cause="Ergonomics",} 0.125
    jvm_gc_pause_seconds_count{action="end of minor GC",cause="Allocation Failure",} 15.0
    jvm_gc_pause_seconds_sum{action="end of minor GC",cause="Allocation Failure",} 0.175
    # HELP jvm_gc_pause_seconds_max Time spent in GC pause
    # TYPE jvm_gc_pause_seconds_max gauge
    jvm_gc_pause_seconds_max{action="end of major GC",cause="Ergonomics",} 0.125
    jvm_gc_pause_seconds_max{action="end of minor GC",cause="Allocation Failure",} 0.002
    # HELP jvm_memory_max_bytes The maximum amount of memory in bytes that can be used for memory management
    # TYPE jvm_memory_max_bytes gauge
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'",} 1.22912768E8
    jvm_memory_max_bytes{area="heap",id="PS Old Gen",} 1.441792E8
    jvm_memory_max_bytes{area="heap",id="PS Survivor Space",} 2097152.0
    jvm_memory_max_bytes{area="heap",id="PS Eden Space",} 6.7633152E7
    jvm_memory_max_bytes{area="nonheap",id="Metaspace",} -1.0
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'non-nmethods'",} 5828608.0
    jvm_memory_max_bytes{area="nonheap",id="Compressed Class Space",} 1.073741824E9
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'",} 1.22916864E8
    # HELP jvm_classes_loaded_classes The number of classes that are currently loaded in the Java virtual machine
    # TYPE jvm_classes_loaded_classes gauge
    jvm_classes_loaded_classes 9085.0
    # HELP jvm_threads_live_threads The current number of live threads including both daemon and non-daemon threads
    # TYPE jvm_threads_live_threads gauge
    jvm_threads_live_threads 20.0
    # HELP CamelExchangesSucceeded_total  
    # TYPE CamelExchangesSucceeded_total counter
    CamelExchangesSucceeded_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService",} 6.0
    CamelExchangesSucceeded_total{camelContext="books-api-v1",routeId="addNewBook-v1",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelExchangesSucceeded_total{camelContext="books-api-v1",routeId="add-new-book-v1",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelExchangesSucceeded_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelExchangesSucceeded_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelExchangesSucceeded_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService",} 6.0
    # HELP process_files_max_files The maximum file descriptor count
    # TYPE process_files_max_files gauge
    process_files_max_files 1048576.0
    # HELP CamelExchangesExternalRedeliveries_total  
    # TYPE CamelExchangesExternalRedeliveries_total counter
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v1",routeId="addNewBook-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v1",routeId="add-new-book-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService",} 0.0
    # HELP jvm_classes_unloaded_classes_total The total number of classes unloaded since the Java virtual machine has started execution
    # TYPE jvm_classes_unloaded_classes_total counter
    jvm_classes_unloaded_classes_total 8.0
    # HELP jvm_threads_daemon_threads The current number of live daemon threads
    # TYPE jvm_threads_daemon_threads gauge
    jvm_threads_daemon_threads 15.0
    # HELP process_uptime_seconds The uptime of the Java virtual machine
    # TYPE process_uptime_seconds gauge
    process_uptime_seconds 210.832
    # HELP CamelRoutesRunning_routes  
    # TYPE CamelRoutesRunning_routes gauge
    CamelRoutesRunning_routes{camelContext="books-api-v1",eventType="RouteEvent",serviceName="MicrometerEventNotifierService",} 6.0
    # HELP system_cpu_count The number of processors available to the Java virtual machine
    # TYPE system_cpu_count gauge
    system_cpu_count 1.0
    # HELP worker_pool_usage_seconds Time spent using resources from the pool
    # TYPE worker_pool_usage_seconds summary
    worker_pool_usage_seconds_count{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_usage_seconds_sum{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_usage_seconds_count{pool_name="vert.x-worker-thread",pool_type="worker",} 24.0
    worker_pool_usage_seconds_sum{pool_name="vert.x-worker-thread",pool_type="worker",} 0.928842853
    # HELP worker_pool_usage_seconds_max Time spent using resources from the pool
    # TYPE worker_pool_usage_seconds_max gauge
    worker_pool_usage_seconds_max{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_usage_seconds_max{pool_name="vert.x-worker-thread",pool_type="worker",} 0.009353003
    # HELP process_files_open_files The open file descriptor count
    # TYPE process_files_open_files gauge
    process_files_open_files 34.0
    # HELP jvm_gc_memory_allocated_bytes_total Incremented for an increase in the size of the (young) heap memory pool after one GC to before the next
    # TYPE jvm_gc_memory_allocated_bytes_total counter
    jvm_gc_memory_allocated_bytes_total 5.13484E7
    # HELP CamelExchangesFailuresHandled_total  
    # TYPE CamelExchangesFailuresHandled_total counter
    CamelExchangesFailuresHandled_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v1",routeId="addNewBook-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v1",routeId="add-new-book-v1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService",} 0.0
    # HELP CamelExchangeEventNotifier_seconds_max  
    # TYPE CamelExchangeEventNotifier_seconds_max gauge
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v1",endpointName="direct://getBooks-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.102
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.606329658
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v1",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.002
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v1",endpointName="direct://addNewBook-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.005
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=POST%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.011022704
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v1",endpointName="platform-http:///api/v1/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.004667676
    # HELP CamelExchangeEventNotifier_seconds  
    # TYPE CamelExchangeEventNotifier_seconds summary
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v1",endpointName="direct://getBooks-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 6.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v1",endpointName="direct://getBooks-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.113
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 6.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.630238922
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v1",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 1.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v1",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.002
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v1",endpointName="direct://addNewBook-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 2.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v1",endpointName="direct://addNewBook-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.007
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=POST%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 2.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=POST%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.015080708
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v1",endpointName="platform-http:///api/v1/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 1.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v1",endpointName="platform-http:///api/v1/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.004667676
    # HELP jvm_gc_overhead_percent An approximation of the percent of CPU time used by GC activities over the last lookback period or since monitoring began, whichever is shorter, in the range [0..1]
    # TYPE jvm_gc_overhead_percent gauge
    jvm_gc_overhead_percent 0.0014730425968623158
    # HELP jvm_memory_used_bytes The amount of used memory
    # TYPE jvm_memory_used_bytes gauge
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'",} 7333760.0
    jvm_memory_used_bytes{area="heap",id="PS Old Gen",} 1.2073352E7
    jvm_memory_used_bytes{area="heap",id="PS Survivor Space",} 1571920.0
    jvm_memory_used_bytes{area="heap",id="PS Eden Space",} 2309368.0
    jvm_memory_used_bytes{area="nonheap",id="Metaspace",} 4.5193984E7
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'non-nmethods'",} 1355008.0
    jvm_memory_used_bytes{area="nonheap",id="Compressed Class Space",} 5938056.0
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'",} 1481984.0
    # HELP system_cpu_usage The "recent cpu usage" of the system the application is running in
    # TYPE system_cpu_usage gauge
    system_cpu_usage 0.3179178062695925
    # HELP http_server_requests_seconds  
    # TYPE http_server_requests_seconds summary
    http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/books",} 6.0
    http_server_requests_seconds_sum{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/books",} 0.637870477
    http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/openapi.json",} 1.0
    http_server_requests_seconds_sum{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/openapi.json",} 0.004978184
    http_server_requests_seconds_count{method="POST",outcome="SUCCESS",status="201",uri="/api/v1/books",} 2.0
    http_server_requests_seconds_sum{method="POST",outcome="SUCCESS",status="201",uri="/api/v1/books",} 0.025903303
    # HELP http_server_requests_seconds_max  
    # TYPE http_server_requests_seconds_max gauge
    http_server_requests_seconds_max{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/books",} 0.611281939
    http_server_requests_seconds_max{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/openapi.json",} 0.004978184
    http_server_requests_seconds_max{method="POST",outcome="SUCCESS",status="201",uri="/api/v1/books",} 0.021169897
    # HELP jvm_info_total JVM version info
    # TYPE jvm_info_total counter
    jvm_info_total{runtime="OpenJDK Runtime Environment",vendor="Red Hat, Inc.",version="17.0.9+9-LTS",} 1.0
    # HELP jvm_gc_max_data_size_bytes Max size of long-lived heap memory pool
    # TYPE jvm_gc_max_data_size_bytes gauge
    jvm_gc_max_data_size_bytes 1.441792E8
    # HELP jvm_threads_states_threads The current number of threads
    # TYPE jvm_threads_states_threads gauge
    jvm_threads_states_threads{state="runnable",} 7.0
    jvm_threads_states_threads{state="blocked",} 0.0
    jvm_threads_states_threads{state="waiting",} 5.0
    jvm_threads_states_threads{state="timed-waiting",} 8.0
    jvm_threads_states_threads{state="new",} 0.0
    jvm_threads_states_threads{state="terminated",} 0.0
    # HELP worker_pool_ratio Pool usage ratio
    # TYPE worker_pool_ratio gauge
    worker_pool_ratio{pool_name="vert.x-internal-blocking",pool_type="worker",} NaN
    worker_pool_ratio{pool_name="vert.x-worker-thread",pool_type="worker",} 0.05
    # HELP worker_pool_queue_size Number of pending elements in the waiting queue
    # TYPE worker_pool_queue_size gauge
    worker_pool_queue_size{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_queue_size{pool_name="vert.x-worker-thread",pool_type="worker",} 0.0
    # HELP jvm_gc_live_data_size_bytes Size of long-lived heap memory pool after reclamation
    # TYPE jvm_gc_live_data_size_bytes gauge
    jvm_gc_live_data_size_bytes 1.0717456E7
    # HELP jvm_buffer_memory_used_bytes An estimate of the memory that the Java virtual machine is using for this buffer pool
    # TYPE jvm_buffer_memory_used_bytes gauge
    jvm_buffer_memory_used_bytes{id="mapped - 'non-volatile memory'",} 0.0
    jvm_buffer_memory_used_bytes{id="mapped",} 0.0
    jvm_buffer_memory_used_bytes{id="direct",} 434208.0
    ```

## Related Guides

- OpenShift ([guide](https://quarkus.io/guides/deploying-to-openshift)): Generate OpenShift resources from annotations
- Camel Platform HTTP ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/platform-http.html)): Expose HTTP endpoints using the HTTP server available in the current platform
- Camel OpenAPI Java ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/openapi-java.html)): Expose OpenAPI resources defined in Camel REST DSL
- Camel MicroProfile Health ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/microprofile-health.html)): Expose Camel health checks via MicroProfile Health
- Camel Jackson ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/jackson.html)): Marshal POJOs to JSON and back using Jackson
- YAML Configuration ([guide](https://quarkus.io/guides/config-yaml)): Use YAML to configure your Quarkus application
- Kubernetes Config ([guide](https://quarkus.io/guides/kubernetes-config)): Read runtime configuration from Kubernetes ConfigMaps and Secrets
- Camel Micrometer ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/micrometer.html)): Collect various metrics directly from Camel routes using the Micrometer library
- Camel Rest ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/rest.html)): Expose REST services and their OpenAPI Specification or call external REST services
- Camel OpenTelemetry ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/opentelemetry.html)): Distributed tracing using OpenTelemetry

## Provided Code

### YAML Config

Configure your application with YAML

[Related guide section...](https://quarkus.io/guides/config-reference#configuration-examples)

The Quarkus application configuration is located in `src/main/resources/application.yml`.
