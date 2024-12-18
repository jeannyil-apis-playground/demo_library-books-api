# books-api-v2

This project leverages **Red Hat build of Quarkus 3.15.x**, the Supersonic Subatomic Java Framework. More specifically, the project is implemented using [**Red Hat build of Apache Camel v4.8.x for Quarkus**](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel).

This project implements a simple REST API that manages books. The following endpoints are exposed:
- `/api/v2/books` : 
    - `GET` method returns a list of all `Books-v2` entities.
    - `POST` method adds a new `book-v2` entity in the inventory.
- `/api/v2/openapi.json`: returns the OpenAPI 3.0 specification for the service.
- `/q/health` : returns the _Camel Quarkus MicroProfile_ health checks
- `/q/metrics` : the _Camel Quarkus Micrometer_ metrics in prometheus format

## Prerequisites

- Maven 3.8.1+
- JDK 21 installed with `JAVA_HOME` configured appropriately
- A running [_Red Hat OpenShift 4_](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell
./mvnw clean compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/books-api-v2-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Deploy the RHCEQ application to OpenShift

### Instructions

1. Login to the OpenShift cluster
    ```shell
    oc login ...
    ```

2. Create an OpenShift project to host the service
    ```shell
    oc new-project ceq-services-jvm --display-name="Red Hat build of Apache Camel for Quarkus Apps - JVM Mode"
    ```

3. Package and deploy the RHCEQ service to OpenShift
    ```shell
    ./mvnw clean package -Dquarkus.openshift.deploy=true -Dquarkus.container-image.group=ceq-services-jvm
    ```

### OpenTelemetry with Jaeger

[**Jaeger**](https://www.jaegertracing.io/), a distributed tracing system for observability ([_open tracing_](https://opentracing.io/)). 

#### Running Jaeger locally

:bulb: A simple way of starting a Jaeger tracing server is with `docker` or `podman`:

1. Start the Jaeger tracing server:
    ```shell
    podman run --rm -e COLLECTOR_ZIPKIN_HOST_PORT=:9411 -e COLLECTOR_OTLP_ENABLED=true \
    -p 6831:6831/udp -p 6832:6832/udp \
    -p 5778:5778 -p 16686:16686 -p 4317:4317 -p 4318:4318 -p 14250:14250  -p 14268:14268 -p 14269:14269 -p 9411:9411 \
    quay.io/jaegertracing/all-in-one:latest
    ```
2. While the server is running, browse to http://localhost:16686 to view tracing events.

#### Deploying Jaeger on OpenShift

1. If not already installed, install the Red Hat OpenShift distributed tracing platform (Jaeger) operator with an AllNamespaces scope.
_**:warning: cluster-admin privileges are required**_
    ```shell
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
    ```shell
    watch oc get sub,csv
    ```

3. Create the allInOne Jaeger instance in the dsna-pilot OpenShift project
    ```shell
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
    ```shell
    URL="http://$(oc get route books-api-v2 -o jsonpath='{.spec.host}')"
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
        Content-Type: application/json
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
    
    - `POST` method:
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
        ```console
        [...]
        HTTP/1.1 201 Created
        [...]
        Content-Type: application/json
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

3. Test the `/api/v2/openapi.json` endpoint
    ```shell
    http $URL/api/v2/openapi.json
    ```
    ```console
    [...]
    HTTP/1.1 200 OK
    [...]
    {
        "components": {
            "schemas": {
                "book-v2": {
                    "description": "A Book (v2) entity",
                    "example": {
                        "author": {
                            "birthDate": "1797-08-30T00:00:00.000Z",
                            "name": "Mary Shelley"
                        },
                        "copies": 10,
                        "title": "Frankenstein",
                        "year": 1818
                    },
                    "properties": {
                        "author": {
                            "properties": {
                                "birthDate": {
                                    "format": "date-time",
                                    "type": "string"
                                },
                                "name": {
                                    "type": "string"
                                }
                            },
                            "type": "object"
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
                    "title": "Root Type for book-v2",
                    "type": "object"
                }
            }
        },
        "info": {
            "description": "Manages a library books inventory",
            "title": "Library Books API (v2)",
            "version": "2.0.0"
        },
        "openapi": "3.0.2",
        "paths": {
            "/books": {
                "description": "The REST endpoint/path used to list and create zero or more `books-v2` entities.  This path contains a `GET` operation to perform the list tasks.",
                "get": {
                    "description": "Gets a list of all `book-v2` entities from the inventory.",
                    "operationId": "getBooks-v2",
                    "responses": {
                        "200": {
                            "content": {
                                "application/json": {
                                    "examples": {
                                        "ListOfBooks-v2": {
                                            "value": [
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
                                                        "name": "Charles Dickens"
                                                    },
                                                    "copies": 3,
                                                    "title": "Pride and Prejudice",
                                                    "year": 1813
                                                }
                                            ]
                                        }
                                    },
                                    "schema": {
                                        "items": {
                                            "$ref": "#/components/schemas/book-v2"
                                        },
                                        "type": "array"
                                    }
                                }
                            },
                            "description": "Successful response - returns an array of `Books-v2` entities."
                        }
                    },
                    "summary": "List all books (v2) from the inventory",
                    "tags": [
                        "Books"
                    ]
                },
                "post": {
                    "description": "Adds a new `book-v2` entity in the inventory.",
                    "operationId": "addNewBook-v2",
                    "requestBody": {
                        "content": {
                            "application/json": {
                                "examples": {
                                    "NewBook-v2": {
                                        "value": {
                                            "author": {
                                                "birthDate": "1642-12-25T00:00:00.000Z",
                                                "name": "Sir Isaac Newton"
                                            },
                                            "copies": 31,
                                            "title": "Philosophiæ Naturalis Principia Mathematica",
                                            "year": 1687
                                        }
                                    }
                                },
                                "schema": {
                                    "$ref": "#/components/schemas/book-v2"
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
                                        "NewListOfBooks-v2": {
                                            "value": [
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
                                        }
                                    },
                                    "schema": {
                                        "items": {
                                            "$ref": "#/components/schemas/book-v2"
                                        },
                                        "type": "array"
                                    }
                                }
                            },
                            "description": "Created"
                        }
                    },
                    "tags": [
                        "Books"
                    ]
                },
                "summary": "Path used to manage the list of books-v2."
            }
        },
        "servers": [
            {
                "description": "Server URL",
                "url": "http://books-api-v2.ceq-services-jvm.svc.cluster.local/api/v2"
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
    ```shell
    http $URL/q/health
    ```
    ```console
    HTTP/1.1 200 OK
    cache-control: private
    content-length: 525
    content-type: application/json; charset=UTF-8
    set-cookie: 10da032c81154e6c6949e0b650b74abb=ac3e90f287c348406733b2023e918176; path=/; HttpOnly

    {
        "checks": [
            {
                "name": "camel-routes",
                "status": "UP"
            },
            {
                "data": {
                    "check.kind": "READINESS",
                    "context.name": "books-api-v2",
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
    ```shell
    http $URL/q/metrics
    ```
    ```console
    [...]
    HTTP/1.1 200 OK
    cache-control: private
    content-length: 25914
    content-type: text/plain; version=0.0.4; charset=utf-8
    set-cookie: 10da032c81154e6c6949e0b650b74abb=04ee9fc4e611d34eb2c882d58a991129; path=/; HttpOnly

    # HELP CamelExchangesInflight  
    # TYPE CamelExchangesInflight gauge
    CamelExchangesInflight{camelContext="books-api-v2",routeId="add-new-book-v2",serviceName="MicrometerEventNotifierService",} 0.0
    CamelExchangesInflight{camelContext="books-api-v2",routeId="get-oas-route",serviceName="MicrometerEventNotifierService",} 0.0
    CamelExchangesInflight{camelContext="books-api-v2",routeId="get-books-v2-route",serviceName="MicrometerEventNotifierService",} 0.0
    # HELP jvm_threads_states_threads The current number of threads
    # TYPE jvm_threads_states_threads gauge
    jvm_threads_states_threads{state="runnable",} 7.0
    jvm_threads_states_threads{state="blocked",} 0.0
    jvm_threads_states_threads{state="waiting",} 5.0
    jvm_threads_states_threads{state="timed-waiting",} 7.0
    jvm_threads_states_threads{state="new",} 0.0
    jvm_threads_states_threads{state="terminated",} 0.0
    # HELP jvm_buffer_total_capacity_bytes An estimate of the total capacity of the buffers in this pool
    # TYPE jvm_buffer_total_capacity_bytes gauge
    jvm_buffer_total_capacity_bytes{id="mapped - 'non-volatile memory'",} 0.0
    jvm_buffer_total_capacity_bytes{id="mapped",} 0.0
    jvm_buffer_total_capacity_bytes{id="direct",} 360479.0
    # HELP jvm_memory_committed_bytes The amount of memory in bytes that is committed for the Java virtual machine to use
    # TYPE jvm_memory_committed_bytes gauge
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'",} 7208960.0
    jvm_memory_committed_bytes{area="heap",id="PS Old Gen",} 1.572864E7
    jvm_memory_committed_bytes{area="heap",id="PS Survivor Space",} 1048576.0
    jvm_memory_committed_bytes{area="heap",id="PS Eden Space",} 2097152.0
    jvm_memory_committed_bytes{area="nonheap",id="Metaspace",} 4.554752E7
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'non-nmethods'",} 2555904.0
    jvm_memory_committed_bytes{area="nonheap",id="Compressed Class Space",} 6094848.0
    jvm_memory_committed_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'",} 2555904.0
    # HELP jvm_classes_loaded_classes The number of classes that are currently loaded in the Java virtual machine
    # TYPE jvm_classes_loaded_classes gauge
    jvm_classes_loaded_classes 9022.0
    # HELP worker_pool_ratio Pool usage ratio
    # TYPE worker_pool_ratio gauge
    worker_pool_ratio{pool_name="vert.x-internal-blocking",pool_type="worker",} NaN
    worker_pool_ratio{pool_name="vert.x-worker-thread",pool_type="worker",} 0.05
    # HELP jvm_buffer_memory_used_bytes An estimate of the memory that the Java virtual machine is using for this buffer pool
    # TYPE jvm_buffer_memory_used_bytes gauge
    jvm_buffer_memory_used_bytes{id="mapped - 'non-volatile memory'",} 0.0
    jvm_buffer_memory_used_bytes{id="mapped",} 0.0
    jvm_buffer_memory_used_bytes{id="direct",} 360480.0
    # HELP CamelExchangesFailed_total  
    # TYPE CamelExchangesFailed_total counter
    CamelExchangesFailed_total{camelContext="books-api-v2",routeId="getBooks-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v2",routeId="addNewBook-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v2",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v2",routeId="add-new-book-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v2",routeId="get-books-v2-route",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailed_total{camelContext="books-api-v2",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.0
    # HELP system_cpu_count The number of processors available to the Java virtual machine
    # TYPE system_cpu_count gauge
    system_cpu_count 1.0
    # HELP jvm_gc_pause_seconds Time spent in GC pause
    # TYPE jvm_gc_pause_seconds summary
    jvm_gc_pause_seconds_count{action="end of major GC",cause="Ergonomics",} 3.0
    jvm_gc_pause_seconds_sum{action="end of major GC",cause="Ergonomics",} 0.375
    jvm_gc_pause_seconds_count{action="end of minor GC",cause="Allocation Failure",} 26.0
    jvm_gc_pause_seconds_sum{action="end of minor GC",cause="Allocation Failure",} 0.227
    # HELP jvm_gc_pause_seconds_max Time spent in GC pause
    # TYPE jvm_gc_pause_seconds_max gauge
    jvm_gc_pause_seconds_max{action="end of major GC",cause="Ergonomics",} 0.071
    jvm_gc_pause_seconds_max{action="end of minor GC",cause="Allocation Failure",} 0.003
    # HELP jvm_buffer_count_buffers An estimate of the number of buffers in the pool
    # TYPE jvm_buffer_count_buffers gauge
    jvm_buffer_count_buffers{id="mapped - 'non-volatile memory'",} 0.0
    jvm_buffer_count_buffers{id="mapped",} 0.0
    jvm_buffer_count_buffers{id="direct",} 11.0
    # HELP CamelExchangeEventNotifier_seconds_max  
    # TYPE CamelExchangeEventNotifier_seconds_max gauge
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v2",endpointName="platform-http:///api/v2/books?httpMethodRestrict=POST%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.006012934
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v2",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.001
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v2",endpointName="direct://getBooks-v2",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.003
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v2",endpointName="platform-http:///api/v2/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.004801888
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v2",endpointName="platform-http:///api/v2/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.005248467
    CamelExchangeEventNotifier_seconds_max{camelContext="books-api-v2",endpointName="direct://addNewBook-v2",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.003
    # HELP CamelExchangeEventNotifier_seconds  
    # TYPE CamelExchangeEventNotifier_seconds summary
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v2",endpointName="platform-http:///api/v2/books?httpMethodRestrict=POST%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 2.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v2",endpointName="platform-http:///api/v2/books?httpMethodRestrict=POST%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.016611304
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v2",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 1.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v2",endpointName="direct://getOAS",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.001
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v2",endpointName="direct://getBooks-v2",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 4.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v2",endpointName="direct://getBooks-v2",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.103
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v2",endpointName="platform-http:///api/v2/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 1.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v2",endpointName="platform-http:///api/v2/openapi.json?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.004801888
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v2",endpointName="platform-http:///api/v2/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 4.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v2",endpointName="platform-http:///api/v2/books?httpMethodRestrict=GET%2COPTIONS",eventType="ExchangeCompletedEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.68594019
    CamelExchangeEventNotifier_seconds_count{camelContext="books-api-v2",endpointName="direct://addNewBook-v2",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 2.0
    CamelExchangeEventNotifier_seconds_sum{camelContext="books-api-v2",endpointName="direct://addNewBook-v2",eventType="ExchangeSentEvent",failed="false",serviceName="MicrometerEventNotifierService",} 0.008
    # HELP jvm_gc_overhead_percent An approximation of the percent of CPU time used by GC activities over the last lookback period or since monitoring began, whichever is shorter, in the range [0..1]
    # TYPE jvm_gc_overhead_percent gauge
    jvm_gc_overhead_percent 2.666666666666667E-4
    # HELP jvm_gc_live_data_size_bytes Size of long-lived heap memory pool after reclamation
    # TYPE jvm_gc_live_data_size_bytes gauge
    jvm_gc_live_data_size_bytes 1.226744E7
    # HELP http_server_bytes_written_max Number of bytes sent by the server
    # TYPE http_server_bytes_written_max gauge
    http_server_bytes_written_max 8192.0
    # HELP http_server_bytes_written Number of bytes sent by the server
    # TYPE http_server_bytes_written summary
    http_server_bytes_written_count 32.0
    http_server_bytes_written_sum 20399.0
    # HELP worker_pool_idle The number of resources from the pool currently used
    # TYPE worker_pool_idle gauge
    worker_pool_idle{pool_name="vert.x-internal-blocking",pool_type="worker",} 20.0
    worker_pool_idle{pool_name="vert.x-worker-thread",pool_type="worker",} 19.0
    # HELP CamelExchangesExternalRedeliveries_total  
    # TYPE CamelExchangesExternalRedeliveries_total counter
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v2",routeId="getBooks-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v2",routeId="addNewBook-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v2",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v2",routeId="add-new-book-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v2",routeId="get-books-v2-route",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesExternalRedeliveries_total{camelContext="books-api-v2",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.0
    # HELP jvm_threads_peak_threads The peak live thread count since the Java virtual machine started or peak was reset
    # TYPE jvm_threads_peak_threads gauge
    jvm_threads_peak_threads 23.0
    # HELP jvm_classes_unloaded_classes_total The total number of classes unloaded since the Java virtual machine has started execution
    # TYPE jvm_classes_unloaded_classes_total counter
    jvm_classes_unloaded_classes_total 63.0
    # HELP worker_pool_usage_seconds Time spent using resources from the pool
    # TYPE worker_pool_usage_seconds summary
    worker_pool_usage_seconds_count{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_usage_seconds_sum{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_usage_seconds_count{pool_name="vert.x-worker-thread",pool_type="worker",} 30.0
    worker_pool_usage_seconds_sum{pool_name="vert.x-worker-thread",pool_type="worker",} 1.021183044
    # HELP worker_pool_usage_seconds_max Time spent using resources from the pool
    # TYPE worker_pool_usage_seconds_max gauge
    worker_pool_usage_seconds_max{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_usage_seconds_max{pool_name="vert.x-worker-thread",pool_type="worker",} 0.004835654
    # HELP http_server_requests_seconds  
    # TYPE http_server_requests_seconds summary
    http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/api/v2/openapi.json",} 1.0
    http_server_requests_seconds_sum{method="GET",outcome="SUCCESS",status="200",uri="/api/v2/openapi.json",} 0.005187043
    http_server_requests_seconds_count{method="GET",outcome="CLIENT_ERROR",status="404",uri="NOT_FOUND",} 1.0
    http_server_requests_seconds_sum{method="GET",outcome="CLIENT_ERROR",status="404",uri="NOT_FOUND",} 7.84367E-4
    http_server_requests_seconds_count{method="POST",outcome="SUCCESS",status="201",uri="/api/v2/books",} 2.0
    http_server_requests_seconds_sum{method="POST",outcome="SUCCESS",status="201",uri="/api/v2/books",} 0.030082518
    http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/api/v2/books",} 4.0
    http_server_requests_seconds_sum{method="GET",outcome="SUCCESS",status="200",uri="/api/v2/books",} 0.692976512
    # HELP http_server_requests_seconds_max  
    # TYPE http_server_requests_seconds_max gauge
    http_server_requests_seconds_max{method="GET",outcome="SUCCESS",status="200",uri="/api/v2/openapi.json",} 0.005187043
    http_server_requests_seconds_max{method="GET",outcome="CLIENT_ERROR",status="404",uri="NOT_FOUND",} 7.84367E-4
    http_server_requests_seconds_max{method="POST",outcome="SUCCESS",status="201",uri="/api/v2/books",} 0.006716851
    http_server_requests_seconds_max{method="GET",outcome="SUCCESS",status="200",uri="/api/v2/books",} 0.005716861
    # HELP jvm_gc_memory_allocated_bytes_total Incremented for an increase in the size of the (young) heap memory pool after one GC to before the next
    # TYPE jvm_gc_memory_allocated_bytes_total counter
    jvm_gc_memory_allocated_bytes_total 5.4279984E7
    # HELP process_uptime_seconds The uptime of the Java virtual machine
    # TYPE process_uptime_seconds gauge
    process_uptime_seconds 344.697
    # HELP process_start_time_seconds Start time of the process since unix epoch.
    # TYPE process_start_time_seconds gauge
    process_start_time_seconds 1.698849522828E9
    # HELP process_files_max_files The maximum file descriptor count
    # TYPE process_files_max_files gauge
    process_files_max_files 1048576.0
    # HELP http_server_connections_seconds_max The duration of the connections
    # TYPE http_server_connections_seconds_max gauge
    http_server_connections_seconds_max 0.153676882
    # HELP http_server_connections_seconds The duration of the connections
    # TYPE http_server_connections_seconds summary
    http_server_connections_seconds_active_count 1.0
    http_server_connections_seconds_duration_sum 0.153513832
    # HELP worker_pool_completed_total Number of times resources from the pool have been acquired
    # TYPE worker_pool_completed_total counter
    worker_pool_completed_total{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_completed_total{pool_name="vert.x-worker-thread",pool_type="worker",} 30.0
    # HELP CamelRoutePolicy_seconds_max  
    # TYPE CamelRoutePolicy_seconds_max gauge
    CamelRoutePolicy_seconds_max{camelContext="books-api-v2",failed="false",routeId="getBooks-v2",serviceName="MicrometerRoutePolicyService",} 0.002744349
    CamelRoutePolicy_seconds_max{camelContext="books-api-v2",failed="false",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.001789734
    CamelRoutePolicy_seconds_max{camelContext="books-api-v2",failed="false",routeId="get-books-v2-route",serviceName="MicrometerRoutePolicyService",} 0.003643108
    CamelRoutePolicy_seconds_max{camelContext="books-api-v2",failed="false",routeId="add-new-book-v2",serviceName="MicrometerRoutePolicyService",} 0.004601907
    CamelRoutePolicy_seconds_max{camelContext="books-api-v2",failed="false",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.003190612
    CamelRoutePolicy_seconds_max{camelContext="books-api-v2",failed="false",routeId="addNewBook-v2",serviceName="MicrometerRoutePolicyService",} 0.002613491
    # HELP CamelRoutePolicy_seconds  
    # TYPE CamelRoutePolicy_seconds summary
    CamelRoutePolicy_seconds_count{camelContext="books-api-v2",failed="false",routeId="getBooks-v2",serviceName="MicrometerRoutePolicyService",} 4.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v2",failed="false",routeId="getBooks-v2",serviceName="MicrometerRoutePolicyService",} 0.101970632
    CamelRoutePolicy_seconds_count{camelContext="books-api-v2",failed="false",routeId="route1",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v2",failed="false",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.001789734
    CamelRoutePolicy_seconds_count{camelContext="books-api-v2",failed="false",routeId="get-books-v2-route",serviceName="MicrometerRoutePolicyService",} 4.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v2",failed="false",routeId="get-books-v2-route",serviceName="MicrometerRoutePolicyService",} 0.589695821
    CamelRoutePolicy_seconds_count{camelContext="books-api-v2",failed="false",routeId="add-new-book-v2",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v2",failed="false",routeId="add-new-book-v2",serviceName="MicrometerRoutePolicyService",} 0.013171345
    CamelRoutePolicy_seconds_count{camelContext="books-api-v2",failed="false",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v2",failed="false",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.003190612
    CamelRoutePolicy_seconds_count{camelContext="books-api-v2",failed="false",routeId="addNewBook-v2",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelRoutePolicy_seconds_sum{camelContext="books-api-v2",failed="false",routeId="addNewBook-v2",serviceName="MicrometerRoutePolicyService",} 0.0083636
    # HELP process_files_open_files The open file descriptor count
    # TYPE process_files_open_files gauge
    process_files_open_files 34.0
    # HELP http_server_bytes_read Number of bytes received by the server
    # TYPE http_server_bytes_read summary
    http_server_bytes_read_count 2.0
    http_server_bytes_read_sum 414.0
    # HELP http_server_bytes_read_max Number of bytes received by the server
    # TYPE http_server_bytes_read_max gauge
    http_server_bytes_read_max 207.0
    # HELP worker_pool_active The number of resources from the pool currently used
    # TYPE worker_pool_active gauge
    worker_pool_active{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_active{pool_name="vert.x-worker-thread",pool_type="worker",} 1.0
    # HELP jvm_gc_max_data_size_bytes Max size of long-lived heap memory pool
    # TYPE jvm_gc_max_data_size_bytes gauge
    jvm_gc_max_data_size_bytes 1.441792E8
    # HELP jvm_memory_usage_after_gc_percent The percentage of long-lived heap pool used after the last GC event, in the range [0..1]
    # TYPE jvm_memory_usage_after_gc_percent gauge
    jvm_memory_usage_after_gc_percent{area="heap",pool="long-lived",} 0.08508467240767045
    # HELP jvm_threads_live_threads The current number of live threads including both daemon and non-daemon threads
    # TYPE jvm_threads_live_threads gauge
    jvm_threads_live_threads 19.0
    # HELP CamelRoutesAdded_routes  
    # TYPE CamelRoutesAdded_routes gauge
    CamelRoutesAdded_routes{camelContext="books-api-v2",eventType="RouteEvent",serviceName="MicrometerEventNotifierService",} 6.0
    # HELP worker_pool_queue_size Number of pending elements in the waiting queue
    # TYPE worker_pool_queue_size gauge
    worker_pool_queue_size{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_queue_size{pool_name="vert.x-worker-thread",pool_type="worker",} 0.0
    # HELP system_cpu_usage The "recent cpu usage" of the system the application is running in
    # TYPE system_cpu_usage gauge
    system_cpu_usage 0.22511145694779117
    # HELP CamelExchangesSucceeded_total  
    # TYPE CamelExchangesSucceeded_total counter
    CamelExchangesSucceeded_total{camelContext="books-api-v2",routeId="getBooks-v2",serviceName="MicrometerRoutePolicyService",} 4.0
    CamelExchangesSucceeded_total{camelContext="books-api-v2",routeId="addNewBook-v2",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelExchangesSucceeded_total{camelContext="books-api-v2",routeId="route1",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelExchangesSucceeded_total{camelContext="books-api-v2",routeId="add-new-book-v2",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelExchangesSucceeded_total{camelContext="books-api-v2",routeId="get-books-v2-route",serviceName="MicrometerRoutePolicyService",} 4.0
    CamelExchangesSucceeded_total{camelContext="books-api-v2",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 1.0
    # HELP jvm_threads_daemon_threads The current number of live daemon threads
    # TYPE jvm_threads_daemon_threads gauge
    jvm_threads_daemon_threads 14.0
    # HELP jvm_gc_memory_promoted_bytes_total Count of positive increases in the size of the old generation memory pool before GC to after GC
    # TYPE jvm_gc_memory_promoted_bytes_total counter
    jvm_gc_memory_promoted_bytes_total 3826696.0
    # HELP system_load_average_1m The sum of the number of runnable entities queued to available processors and the number of runnable entities running on the available processors averaged over a period of time
    # TYPE system_load_average_1m gauge
    system_load_average_1m 0.67
    # HELP CamelExchangesTotal_total  
    # TYPE CamelExchangesTotal_total counter
    CamelExchangesTotal_total{camelContext="books-api-v2",routeId="getBooks-v2",serviceName="MicrometerRoutePolicyService",} 4.0
    CamelExchangesTotal_total{camelContext="books-api-v2",routeId="addNewBook-v2",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelExchangesTotal_total{camelContext="books-api-v2",routeId="route1",serviceName="MicrometerRoutePolicyService",} 1.0
    CamelExchangesTotal_total{camelContext="books-api-v2",routeId="add-new-book-v2",serviceName="MicrometerRoutePolicyService",} 2.0
    CamelExchangesTotal_total{camelContext="books-api-v2",routeId="get-books-v2-route",serviceName="MicrometerRoutePolicyService",} 4.0
    CamelExchangesTotal_total{camelContext="books-api-v2",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 1.0
    # HELP CamelRoutesRunning_routes  
    # TYPE CamelRoutesRunning_routes gauge
    CamelRoutesRunning_routes{camelContext="books-api-v2",eventType="RouteEvent",serviceName="MicrometerEventNotifierService",} 6.0
    # HELP worker_pool_queue_delay_seconds_max Time spent in the waiting queue before being processed
    # TYPE worker_pool_queue_delay_seconds_max gauge
    worker_pool_queue_delay_seconds_max{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_queue_delay_seconds_max{pool_name="vert.x-worker-thread",pool_type="worker",} 2.53788E-4
    # HELP worker_pool_queue_delay_seconds Time spent in the waiting queue before being processed
    # TYPE worker_pool_queue_delay_seconds summary
    worker_pool_queue_delay_seconds_count{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_queue_delay_seconds_sum{pool_name="vert.x-internal-blocking",pool_type="worker",} 0.0
    worker_pool_queue_delay_seconds_count{pool_name="vert.x-worker-thread",pool_type="worker",} 31.0
    worker_pool_queue_delay_seconds_sum{pool_name="vert.x-worker-thread",pool_type="worker",} 0.005738803
    # HELP process_cpu_usage The "recent cpu usage" for the Java Virtual Machine process
    # TYPE process_cpu_usage gauge
    process_cpu_usage 0.22289156626506024
    # HELP jvm_memory_max_bytes The maximum amount of memory in bytes that can be used for memory management
    # TYPE jvm_memory_max_bytes gauge
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'",} 1.22912768E8
    jvm_memory_max_bytes{area="heap",id="PS Old Gen",} 1.441792E8
    jvm_memory_max_bytes{area="heap",id="PS Survivor Space",} 1048576.0
    jvm_memory_max_bytes{area="heap",id="PS Eden Space",} 6.9730304E7
    jvm_memory_max_bytes{area="nonheap",id="Metaspace",} -1.0
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'non-nmethods'",} 5828608.0
    jvm_memory_max_bytes{area="nonheap",id="Compressed Class Space",} 1.073741824E9
    jvm_memory_max_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'",} 1.22916864E8
    # HELP jvm_info_total JVM version info
    # TYPE jvm_info_total counter
    jvm_info_total{runtime="OpenJDK Runtime Environment",vendor="Red Hat, Inc.",version="17.0.9+9-LTS",} 1.0
    # HELP jvm_memory_used_bytes The amount of used memory
    # TYPE jvm_memory_used_bytes gauge
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'",} 7236352.0
    jvm_memory_used_bytes{area="heap",id="PS Old Gen",} 1.226744E7
    jvm_memory_used_bytes{area="heap",id="PS Survivor Space",} 0.0
    jvm_memory_used_bytes{area="heap",id="PS Eden Space",} 1167608.0
    jvm_memory_used_bytes{area="nonheap",id="Metaspace",} 4.5163352E7
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'non-nmethods'",} 1355136.0
    jvm_memory_used_bytes{area="nonheap",id="Compressed Class Space",} 5922184.0
    jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'non-profiled nmethods'",} 1550208.0
    # HELP CamelExchangesFailuresHandled_total  
    # TYPE CamelExchangesFailuresHandled_total counter
    CamelExchangesFailuresHandled_total{camelContext="books-api-v2",routeId="getBooks-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v2",routeId="addNewBook-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v2",routeId="route1",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v2",routeId="add-new-book-v2",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v2",routeId="get-books-v2-route",serviceName="MicrometerRoutePolicyService",} 0.0
    CamelExchangesFailuresHandled_total{camelContext="books-api-v2",routeId="get-oas-route",serviceName="MicrometerRoutePolicyService",} 0.0
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
