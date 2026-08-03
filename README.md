# ImageSearchAPI
Web app that acts as an API proxy of Apache Solr taht contains web archived images.

## Build

It produces a self-executable war file with an embedded Tomcat.

```bash
mvn clean package
java -jar target/image-search-api.war
```

## Docker

A `Dockerfile` is provided to build and run the app in a container:

```bash
docker build -t image-search-api .
docker run -p 8080:8080 image-search-api
```

## Production deployment with Docker

The image ships with default JVM flags (see `JAVA_OPTS` in the `Dockerfile`) tuned for running in a memory-constrained container:

- `-XX:+UseG1GC` — G1 instead of JDK 8's default Parallel GC, for more predictable pause times on a request/response API.
- `-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0` — size the heap as a percentage of the container's memory limit instead of a fixed `-Xmx`/`-Xms`, so the same image behaves correctly across environments with different memory limits.
- `-XX:+ExitOnOutOfMemoryError` — exit on OOM instead of limping along in a broken state, so the orchestrator restarts the container.

**These percentage-based flags only work correctly if the container has an explicit memory limit** — without one, the JVM falls back to sizing off the host's total memory, which is not what you want in production. Always set a memory limit when running the container:

```bash
docker run -p 8080:8080 --memory=4g image-search-api
```

In Kubernetes, set the equivalent `resources.limits.memory` (and match `requests.memory` to it for predictable scheduling/sizing).

**Recommended memory limit: 4 GiB.** Load testing (`ab`/`curl` with varied queries and `maxItems=200`) compared 1 GiB, 2 GiB, and 4 GiB limits. Because G1's young-generation sizing scales as a percentage of whatever heap ceiling it's given, raw "% memory used at peak" isn't directly comparable across different limits — G1 will use more of a bigger ceiling for throughput even when the actual live/necessary working set hasn't changed. The more meaningful signal is distress indicators: Full GC events, "to-space exhausted" evacuation failures (heap pressure during a collection), and actual OOM-kills.

| Memory limit | Concurrency | Peak memory | To-space exhausted | Full GC | OOM-kill |
|---|---|---|---|---|---|
| 1 GiB | ~150 | 100% (no headroom) | 14 | 0 | no, but no margin left |
| 2 GiB | ~150 | ~89% | 2 | 0 | no |
| 4 GiB | ~250 | ~84% | 2 | 0 | no |

The error rate was the same (~0.7-1%) across all three runs and came from the backend Solr server, not container memory — confirmed by reproducing the same errors directly against Solr outside the app. 1 GiB is unsafe: it leaves no headroom for the next load spike, since JDK 8's G1 does not release committed heap back to the OS afterwards, so usage stays pinned near the ceiling. 4 GiB was chosen over 2 GiB to leave comfortable margin for traffic spikes above the ~150-250 concurrent requests exercised here, and to leave room for raising Tomcat's thread pool size (see below) without immediately re-approaching the memory ceiling. With `MaxRAMPercentage=75.0` that's a 3 GiB heap ceiling and ~1 GiB for Metaspace, thread stacks, code cache, and direct/NIO buffers.

This number is based on synthetic load, not real arquivo.pt production traffic — revisit it once real peak-concurrency numbers are available.

To override the defaults (e.g. a different heap percentage, or disabling G1), set `JAVA_OPTS` at runtime — it replaces the Dockerfile's default entirely:

```bash
docker run -p 8080:8080 --memory=4g \
  -e JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=80.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError" \
  image-search-api
```

### Concurrency limit: Tomcat's thread pool

Independently of container memory, Spring Boot's embedded Tomcat caps concurrent request processing with its own defaults, unmodified in this project: `server.tomcat.threads.max=200` (max worker threads) and `server.tomcat.accept-count=100` (extra connections queued once all 200 threads are busy, beyond which new connections are refused). This is the actual backstop against unbounded traffic spikes — raising the memory limit alone doesn't increase how many requests can be served concurrently, it only gives the JVM more room to run at the existing 200-thread ceiling.

To raise the ceiling, pass the equivalent Spring Boot properties as JVM system properties in `JAVA_OPTS` (or as `SERVER_TOMCAT_THREADS_MAX/SERVER_TOMCAT_ACCEPT_COUNT` environment variables, via Spring's relaxed env binding):

```bash
docker run -p 8080:8080 --memory=4g \
  -e JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Dserver.tomcat.threads.max=400 -Dserver.tomcat.accept-count=200" \
  image-search-api
```

Raising the thread cap increases the number of requests that can be in flight at once, and each one holds its own thread stack and request/response buffers — so it also raises the memory the JVM can actually use under load. Re-run load testing at the new thread count before increasing it in production, and scale the memory limit up alongside it rather than in isolation.

## Development

To make development more rapid there is a docker-compose.yml file that runs the web application inside a docker.

Example run

```bash
docker-compose up --build
```

Example with custom solr server:

```bash
docker-compose build --build-arg SOLR_SERVER=p51.arquivo.pt && docker-compose up
```
