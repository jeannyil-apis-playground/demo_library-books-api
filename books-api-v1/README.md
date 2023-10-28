# books-api-v1

This project leverages **Red Hat build of Quarkus 3.2.x**, the Supersonic Subatomic Java Framework. More specifically, the project is implemented using [**Red Hat Camel Extensions for Quarkus (RHCEQ) 3.2.x**](https://access.redhat.com/documentation/en-us/red_hat_integration/2023.q1/html/getting_started_with_camel_extensions_for_quarkus/index).

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

3. Test the `/api/v1/openapi.json` endpoint
    ```shell script
    http -v $URL/api/v1/openapi.json
    ```
    ```console
    [...]
    HTTP/1.1 200 OK
    [...]
    {
        "components": {
            "schemas": {
                "books-v1": {
                    "description": "List of Books (v1)",
                    "example": [
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
                    ],
                    "items": {
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
                        "type": "object"
                    },
                    "title": "Root Type for books-v1",
                    "type": "array"
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
                    "description": "Gets a list of all `Books-v1` entities.",
                    "operationId": "getBooks-v1",
                    "responses": {
                        "200": {
                            "content": {
                                "application/json": {
                                    "examples": {
                                        "Books-v1": {
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
                                            "$ref": "#/components/schemas/books-v1"
                                        },
                                        "type": "array"
                                    }
                                }
                            },
                            "description": "Successful response - returns an array of `books-v1` entities."
                        }
                    },
                    "summary": "List All books-v1",
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
    http -v $URL/q/health
    ```
    ```console
    HTTP/1.1 200 OK
    cache-control: private
    content-length: 869
    content-type: application/json; charset=UTF-8
    set-cookie: 9dcf4baeb4aaf2796beba21cfecc7c84=856c62033c73b0742c8900676fff2289; path=/; HttpOnly

    {
        "checks": [
            {
                "data": {
                    "check.kind": "ALL",
                    "context.name": "books-api-v1",
                    "context.phase": "5",
                    "context.status": "Started",
                    "context.version": "4.0.0"
                },
                "name": "context",
                "status": "UP"
            },
            {
                "name": "camel-routes",
                "status": "UP"
            },
            {
                "data": {
                    "check.kind": "ALL",
                    "context.name": "books-api-v1",
                    "context.phase": "5",
                    "context.status": "Started",
                    "context.version": "4.0.0"
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
    http -v $URL/q/metrics
    ```
    ```console
    [...]
    HTTP/1.1 200 OK
    cache-control: private
    content-length: 23062
    content-type: application/openmetrics-text; version=1.0.0; charset=utf-8
    set-cookie: 9dcf4baeb4aaf2796beba21cfecc7c84=856c62033c73b0742c8900676fff2289; path=/; HttpOnly

    # TYPE worker_pool_queue_size gauge
    # HELP worker_pool_queue_size Number of pending elements in the waiting queue
    worker_pool_queue_size{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_queue_size{pool_name="vert.x-worker-thread",pool_type="worker"} 0.0
    # TYPE worker_pool_idle gauge
    # HELP worker_pool_idle The number of resources from the pool currently used
    worker_pool_idle{pool_name="vert.x-internal-blocking",pool_type="worker"} 20.0
    worker_pool_idle{pool_name="vert.x-worker-thread",pool_type="worker"} 19.0
    # TYPE jvm_memory_max_bytes gauge
    # HELP jvm_memory_max_bytes The maximum amount of memory in bytes that can be used for memory management
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'"} 1.22912768E8
    jvm_memory_max_bytes{area="heap",id="PS Old Gen"} 1.441792E8
    jvm_memory_max_bytes{area="heap",id="PS Survivor Space"} 1048576.0
    jvm_memory_max_bytes{area="heap",id="PS Eden Space"} 6.9730304E7
    jvm_memory_max_bytes{area="nonheap",id="Metaspace"} -1.0
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'non-nmethods'"} 5828608.0
    jvm_memory_max_bytes{area="nonheap",id="Compressed Class Space"} 1.073741824E9
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'"} 1.22916864E8
    # TYPE system_load_average_1m gauge
    # HELP system_load_average_1m The sum of the number of runnable entities queued to available processors and the number of runnable entities running on the available processors averaged over a period of time
    system_load_average_1m 4.65
    # TYPE camel_exchanges counter
    # HELP camel_exchanges Total number of processed exchanges
    camel_exchanges_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService"} 4.0 # {span_id="5b6c845929891da6",trace_id="c23dec07ed0c872578cd146b7d8f864d"} 1.0 1698517666.232
    camel_exchanges_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService"} 2.0 # {span_id="718823ab465c7700",trace_id="248a3fba31d24f0839b2b99d39ab79b5"} 1.0 1698517783.068
    camel_exchanges_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService"} 2.0 # {span_id="13f8dc9fa0c4e5ff",trace_id="248a3fba31d24f0839b2b99d39ab79b5"} 1.0 1698517783.068
    camel_exchanges_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService"} 4.0 # {span_id="5bfdc681ee83c60c",trace_id="c23dec07ed0c872578cd146b7d8f864d"} 1.0 1698517666.232
    # TYPE process_uptime_seconds gauge
    # HELP process_uptime_seconds The uptime of the Java virtual machine
    process_uptime_seconds 1666.865
    # TYPE worker_pool_active gauge
    # HELP worker_pool_active The number of resources from the pool currently used
    worker_pool_active{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_active{pool_name="vert.x-worker-thread",pool_type="worker"} 1.0
    # TYPE jvm_buffer_count_buffers gauge
    # HELP jvm_buffer_count_buffers An estimate of the number of buffers in the pool
    jvm_buffer_count_buffers{id="mapped - 'non-volatile memory'"} 0.0
    jvm_buffer_count_buffers{id="mapped"} 0.0
    jvm_buffer_count_buffers{id="direct"} 21.0
    # TYPE jvm_threads_started_threads counter
    # HELP jvm_threads_started_threads The total number of application threads started in the JVM
    jvm_threads_started_threads_total 41.0
    # TYPE jvm_gc_pause_seconds summary
    # HELP jvm_gc_pause_seconds Time spent in GC pause
    jvm_gc_pause_seconds_count{action="end of minor GC",cause="Allocation Failure",gc="PS Scavenge"} 21.0
    jvm_gc_pause_seconds_sum{action="end of minor GC",cause="Allocation Failure",gc="PS Scavenge"} 0.141
    jvm_gc_pause_seconds_count{action="end of major GC",cause="Ergonomics",gc="PS MarkSweep"} 1.0
    jvm_gc_pause_seconds_sum{action="end of major GC",cause="Ergonomics",gc="PS MarkSweep"} 0.124
    # TYPE jvm_gc_pause_seconds_max gauge
    # HELP jvm_gc_pause_seconds_max Time spent in GC pause
    jvm_gc_pause_seconds_max{action="end of minor GC",cause="Allocation Failure",gc="PS Scavenge"} 0.003
    jvm_gc_pause_seconds_max{action="end of major GC",cause="Ergonomics",gc="PS MarkSweep"} 0.0
    # TYPE jvm_buffer_total_capacity_bytes gauge
    # HELP jvm_buffer_total_capacity_bytes An estimate of the total capacity of the buffers in this pool
    jvm_buffer_total_capacity_bytes{id="mapped - 'non-volatile memory'"} 0.0
    jvm_buffer_total_capacity_bytes{id="mapped"} 0.0
    jvm_buffer_total_capacity_bytes{id="direct"} 950467.0
    # TYPE jvm_gc_overhead_percent gauge
    # HELP jvm_gc_overhead_percent An approximation of the percent of CPU time used by GC activities over the last lookback period or since monitoring began, whichever is shorter, in the range [0..1]
    jvm_gc_overhead_percent 1.0E-5
    # TYPE jvm_threads_peak_threads gauge
    # HELP jvm_threads_peak_threads The peak live thread count since the Java virtual machine started or peak was reset
    jvm_threads_peak_threads 27.0
    # TYPE jvm_memory_usage_after_gc_percent gauge
    # HELP jvm_memory_usage_after_gc_percent The percentage of long-lived heap pool used after the last GC event, in the range [0..1]
    jvm_memory_usage_after_gc_percent{area="heap",pool="long-lived"} 0.12126725630326704
    # TYPE jvm_memory_committed_bytes gauge
    # HELP jvm_memory_committed_bytes The amount of memory in bytes that is committed for the Java virtual machine to use
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'"} 1.1075584E7
    jvm_memory_committed_bytes{area="heap",id="PS Old Gen"} 2.0447232E7
    jvm_memory_committed_bytes{area="heap",id="PS Survivor Space"} 1048576.0
    jvm_memory_committed_bytes{area="heap",id="PS Eden Space"} 2621440.0
    jvm_memory_committed_bytes{area="nonheap",id="Metaspace"} 5.4001664E7
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'non-nmethods'"} 2555904.0
    jvm_memory_committed_bytes{area="nonheap",id="Compressed Class Space"} 7012352.0
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'"} 2555904.0
    # TYPE jvm_threads_live_threads gauge
    # HELP jvm_threads_live_threads The current number of live threads including both daemon and non-daemon threads
    jvm_threads_live_threads 24.0
    # TYPE camel_routes_added_routes gauge
    # HELP camel_routes_added_routes  
    camel_routes_added_routes{camelContext="books-api-v1",eventType="RouteEvent",serviceName="MicrometerEventNotifierService"} 4.0
    # TYPE system_cpu_usage gauge
    # HELP system_cpu_usage The \"recent cpu usage\" of the system the application is running in
    system_cpu_usage 0.09175393833876222
    # TYPE camel_exchanges_failures_handled counter
    # HELP camel_exchanges_failures_handled Number of failures handled
    camel_exchanges_failures_handled_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_failures_handled_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_failures_handled_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_failures_handled_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService"} 0.0
    # TYPE http_server_connections_seconds_max gauge
    # HELP http_server_connections_seconds_max The duration of the connections
    http_server_connections_seconds_max 0.009716051
    # TYPE http_server_connections_seconds summary
    # HELP http_server_connections_seconds The duration of the connections
    http_server_connections_seconds_active_count 1.0
    http_server_connections_seconds_duration_sum 0.009586215
    # TYPE http_server_bytes_written_max gauge
    # HELP http_server_bytes_written_max Number of bytes sent by the server
    http_server_bytes_written_max 869.0
    # TYPE http_server_bytes_written summary
    # HELP http_server_bytes_written Number of bytes sent by the server
    http_server_bytes_written_count 345.0
    http_server_bytes_written_sum 163699.0
    # TYPE http_server_bytes_read summary
    # HELP http_server_bytes_read Number of bytes received by the server
    http_server_bytes_read_count 0.0
    http_server_bytes_read_sum 0.0
    # TYPE http_server_bytes_read_max gauge
    # HELP http_server_bytes_read_max Number of bytes received by the server
    http_server_bytes_read_max 0.0
    # TYPE jvm_gc_memory_allocated_bytes counter
    # HELP jvm_gc_memory_allocated_bytes Incremented for an increase in the size of the (young) heap memory pool after one GC to before the next
    jvm_gc_memory_allocated_bytes_total 5.4956112E7
    # TYPE jvm_classes_unloaded_classes counter
    # HELP jvm_classes_unloaded_classes The total number of classes unloaded since the Java virtual machine has started execution
    jvm_classes_unloaded_classes_total 0.0
    # TYPE jvm_gc_max_data_size_bytes gauge
    # HELP jvm_gc_max_data_size_bytes Max size of long-lived heap memory pool
    jvm_gc_max_data_size_bytes 1.441792E8
    # TYPE worker_pool_completed counter
    # HELP worker_pool_completed Number of times resources from the pool have been acquired
    worker_pool_completed_total{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_completed_total{pool_name="vert.x-worker-thread",pool_type="worker"} 341.0 # {span_id="13f8dc9fa0c4e5ff",trace_id="248a3fba31d24f0839b2b99d39ab79b5"} 1.0 1698517783.069
    # TYPE jvm_info counter
    # HELP jvm_info JVM version info
    jvm_info_total{runtime="OpenJDK Runtime Environment",vendor="Red Hat, Inc.",version="17.0.9+9-LTS"} 1.0
    # TYPE camel_exchanges_external_redeliveries counter
    # HELP camel_exchanges_external_redeliveries Number of external initiated redeliveries (such as from JMS broker)
    camel_exchanges_external_redeliveries_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_external_redeliveries_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_external_redeliveries_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_external_redeliveries_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService"} 0.0
    # TYPE camel_exchanges_inflight gauge
    # HELP camel_exchanges_inflight Route inflight messages
    camel_exchanges_inflight{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerEventNotifierService"} 0.0
    camel_exchanges_inflight{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerEventNotifierService"} 0.0
    # TYPE jvm_threads_states_threads gauge
    # HELP jvm_threads_states_threads The current number of threads
    jvm_threads_states_threads{state="runnable"} 11.0
    jvm_threads_states_threads{state="blocked"} 0.0
    jvm_threads_states_threads{state="waiting"} 6.0
    jvm_threads_states_threads{state="timed-waiting"} 7.0
    jvm_threads_states_threads{state="new"} 0.0
    jvm_threads_states_threads{state="terminated"} 0.0
    # TYPE camel_routes_running_routes gauge
    # HELP camel_routes_running_routes  
    camel_routes_running_routes{camelContext="books-api-v1",eventType="RouteEvent",serviceName="MicrometerEventNotifierService"} 4.0
    # TYPE process_files_max_files gauge
    # HELP process_files_max_files The maximum file descriptor count
    process_files_max_files 1048576.0
    # TYPE camel_exchanges_succeeded counter
    # HELP camel_exchanges_succeeded Number of successfully completed exchanges
    camel_exchanges_succeeded_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService"} 4.0 # {span_id="5b6c845929891da6",trace_id="c23dec07ed0c872578cd146b7d8f864d"} 1.0 1698517666.232
    camel_exchanges_succeeded_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService"} 2.0 # {span_id="718823ab465c7700",trace_id="248a3fba31d24f0839b2b99d39ab79b5"} 1.0 1698517783.068
    camel_exchanges_succeeded_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService"} 2.0 # {span_id="13f8dc9fa0c4e5ff",trace_id="248a3fba31d24f0839b2b99d39ab79b5"} 1.0 1698517783.068
    camel_exchanges_succeeded_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService"} 4.0 # {span_id="5bfdc681ee83c60c",trace_id="c23dec07ed0c872578cd146b7d8f864d"} 1.0 1698517666.232
    # TYPE http_server_active_requests gauge
    # HELP http_server_active_requests  
    http_server_active_requests 1.0
    # TYPE jvm_gc_memory_promoted_bytes counter
    # HELP jvm_gc_memory_promoted_bytes Count of positive increases in the size of the old generation memory pool before GC to after GC
    jvm_gc_memory_promoted_bytes_total 4059688.0
    # TYPE worker_pool_ratio gauge
    # HELP worker_pool_ratio Pool usage ratio
    worker_pool_ratio{pool_name="vert.x-internal-blocking",pool_type="worker"} NaN
    worker_pool_ratio{pool_name="vert.x-worker-thread",pool_type="worker"} 0.05
    # TYPE worker_pool_usage_seconds summary
    # HELP worker_pool_usage_seconds Time spent using resources from the pool
    worker_pool_usage_seconds_count{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_usage_seconds_sum{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_usage_seconds_count{pool_name="vert.x-worker-thread",pool_type="worker"} 341.0
    worker_pool_usage_seconds_sum{pool_name="vert.x-worker-thread",pool_type="worker"} 0.560966301
    # TYPE worker_pool_usage_seconds_max gauge
    # HELP worker_pool_usage_seconds_max Time spent using resources from the pool
    worker_pool_usage_seconds_max{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_usage_seconds_max{pool_name="vert.x-worker-thread",pool_type="worker"} 8.34297E-4
    # TYPE process_files_open_files gauge
    # HELP process_files_open_files The open file descriptor count
    process_files_open_files 42.0
    # TYPE jvm_gc_live_data_size_bytes gauge
    # HELP jvm_gc_live_data_size_bytes Size of long-lived heap memory pool after reclamation
    jvm_gc_live_data_size_bytes 1.6341648E7
    # TYPE camel_route_policy_seconds summary
    # HELP camel_route_policy_seconds Route performance metrics
    camel_route_policy_seconds_count{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService"} 4.0
    camel_route_policy_seconds_sum{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService"} 0.100014126
    camel_route_policy_seconds_count{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService"} 2.0
    camel_route_policy_seconds_sum{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService"} 0.009995004
    camel_route_policy_seconds_count{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService"} 2.0
    camel_route_policy_seconds_sum{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService"} 0.096756579
    camel_route_policy_seconds_count{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService"} 4.0
    camel_route_policy_seconds_sum{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService"} 0.106631313
    # TYPE camel_route_policy_seconds_max gauge
    # HELP camel_route_policy_seconds_max Route performance metrics
    camel_route_policy_seconds_max{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_route_policy_seconds_max{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_route_policy_seconds_max{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_route_policy_seconds_max{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService"} 0.0
    # TYPE camel_exchange_event_notifier_seconds_max gauge
    # HELP camel_exchange_event_notifier_seconds_max Time taken to send message to the endpoint
    camel_exchange_event_notifier_seconds_max{camelContext="books-api-v1",endpointName="direct://getBooks-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService"} 0.0
    camel_exchange_event_notifier_seconds_max{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService"} 0.0
    camel_exchange_event_notifier_seconds_max{camelContext="books-api-v1",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService"} 0.0
    camel_exchange_event_notifier_seconds_max{camelContext="books-api-v1",endpointName="platform-http:///api/v1/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService"} 0.0
    # TYPE camel_exchange_event_notifier_seconds summary
    # HELP camel_exchange_event_notifier_seconds Time taken to send message to the endpoint
    camel_exchange_event_notifier_seconds_count{camelContext="books-api-v1",endpointName="direct://getBooks-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService"} 4.0
    camel_exchange_event_notifier_seconds_sum{camelContext="books-api-v1",endpointName="direct://getBooks-v1",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService"} 0.099
    camel_exchange_event_notifier_seconds_count{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService"} 4.0
    camel_exchange_event_notifier_seconds_sum{camelContext="books-api-v1",endpointName="platform-http:///api/v1/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService"} 0.113525322
    camel_exchange_event_notifier_seconds_count{camelContext="books-api-v1",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService"} 2.0
    camel_exchange_event_notifier_seconds_sum{camelContext="books-api-v1",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService"} 0.011
    camel_exchange_event_notifier_seconds_count{camelContext="books-api-v1",endpointName="platform-http:///api/v1/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService"} 2.0
    camel_exchange_event_notifier_seconds_sum{camelContext="books-api-v1",endpointName="platform-http:///api/v1/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService"} 0.187767684
    # TYPE worker_pool_queue_delay_seconds_max gauge
    # HELP worker_pool_queue_delay_seconds_max Time spent in the waiting queue before being processed
    worker_pool_queue_delay_seconds_max{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_queue_delay_seconds_max{pool_name="vert.x-worker-thread",pool_type="worker"} 6.3226E-5
    # TYPE worker_pool_queue_delay_seconds summary
    # HELP worker_pool_queue_delay_seconds Time spent in the waiting queue before being processed
    worker_pool_queue_delay_seconds_count{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_queue_delay_seconds_sum{pool_name="vert.x-internal-blocking",pool_type="worker"} 0.0
    worker_pool_queue_delay_seconds_count{pool_name="vert.x-worker-thread",pool_type="worker"} 342.0
    worker_pool_queue_delay_seconds_sum{pool_name="vert.x-worker-thread",pool_type="worker"} 0.044562246
    # TYPE camel_exchanges_failed counter
    # HELP camel_exchanges_failed Number of failed exchanges
    camel_exchanges_failed_total{camelContext="books-api-v1",routeId="getBooks-v1",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_failed_total{camelContext="books-api-v1",routeId="route1",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_failed_total{camelContext="books-api-v1",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService"} 0.0
    camel_exchanges_failed_total{camelContext="books-api-v1",routeId="get-books-v1-route",serviceName="MicrometerRoutePolicyService"} 0.0
    # TYPE http_server_requests_seconds summary
    # HELP http_server_requests_seconds  
    http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/books"} 4.0
    http_server_requests_seconds_sum{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/books"} 0.115271831
    http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/openapi.json"} 2.0
    http_server_requests_seconds_sum{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/openapi.json"} 0.196634934
    http_server_requests_seconds_count{method="GET",outcome="CLIENT_ERROR",status="404",uri="NOT_FOUND"} 1.0
    http_server_requests_seconds_sum{method="GET",outcome="CLIENT_ERROR",status="404",uri="NOT_FOUND"} 0.001069204
    # TYPE http_server_requests_seconds_max gauge
    # HELP http_server_requests_seconds_max  
    http_server_requests_seconds_max{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/books"} 0.0
    http_server_requests_seconds_max{method="GET",outcome="SUCCESS",status="200",uri="/api/v1/openapi.json"} 0.0
    http_server_requests_seconds_max{method="GET",outcome="CLIENT_ERROR",status="404",uri="NOT_FOUND"} 0.0
    # TYPE jvm_memory_used_bytes gauge
    # HELP jvm_memory_used_bytes The amount of used memory
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'"} 1.1045376E7
    jvm_memory_used_bytes{area="heap",id="PS Old Gen"} 1.7484216E7
    jvm_memory_used_bytes{area="heap",id="PS Survivor Space"} 854232.0
    jvm_memory_used_bytes{area="heap",id="PS Eden Space"} 2157984.0
    jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 5.3627888E7
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'non-nmethods'"} 1406336.0
    jvm_memory_used_bytes{area="nonheap",id="Compressed Class Space"} 6846808.0
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'"} 1917440.0
    # TYPE jvm_threads_daemon_threads gauge
    # HELP jvm_threads_daemon_threads The current number of live daemon threads
    jvm_threads_daemon_threads 13.0
    # TYPE process_start_time_seconds gauge
    # HELP process_start_time_seconds Start time of the process since unix epoch.
    process_start_time_seconds 1.698516397922E9
    # TYPE jvm_classes_loaded_classes gauge
    # HELP jvm_classes_loaded_classes The number of classes that are currently loaded in the Java virtual machine
    jvm_classes_loaded_classes 10337.0
    # TYPE system_cpu_count gauge
    # HELP system_cpu_count The number of processors available to the Java virtual machine
    system_cpu_count 1.0
    # TYPE process_cpu_usage gauge
    # HELP process_cpu_usage The \"recent cpu usage\" for the Java Virtual Machine process
    process_cpu_usage 0.09131378935939197
    # TYPE jvm_buffer_memory_used_bytes gauge
    # HELP jvm_buffer_memory_used_bytes An estimate of the memory that the Java virtual machine is using for this buffer pool
    jvm_buffer_memory_used_bytes{id="mapped - 'non-volatile memory'"} 0.0
    jvm_buffer_memory_used_bytes{id="mapped"} 0.0
    jvm_buffer_memory_used_bytes{id="direct"} 958660.0
    # EOF
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
