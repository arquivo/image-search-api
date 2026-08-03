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
docker run -p 8080:8080 --memory=2g image-search-api
```

In Kubernetes, set the equivalent `resources.limits.memory` (and match `requests.memory` to it for predictable scheduling/sizing).

**Recommended memory limit: 2 GiB.** Load testing (`ab`/`curl` with varied queries and `maxItems=200`, up to ~150 concurrent requests) showed a 1 GiB limit leaves too little headroom: memory climbed to 100% of the limit under a single burst of load, with 14 G1 "to-space exhausted" events (evacuation failures caused by heap pressure) during the run. This is expected on JDK 8 — G1 does not release committed heap back to the OS after a load spike, so memory usage stays pinned near the ceiling indefinitely rather than settling back down, leaving no headroom for the next burst. The same test against a 2 GiB limit peaked at ~89% (~230 MiB of headroom) with only 2 to-space-exhausted events and the same error rate as the 1 GiB run (the small number of errors observed in both runs came from the backend Solr server, not the container's memory). With `MaxRAMPercentage=75.0` that's a ~1.5 GiB heap ceiling and ~500 MiB for Metaspace, thread stacks, code cache, and direct/NIO buffers.

To override the defaults (e.g. a different heap percentage, or disabling G1), set `JAVA_OPTS` at runtime — it replaces the Dockerfile's default entirely:

```bash
docker run -p 8080:8080 --memory=4g \
  -e JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=80.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError" \
  image-search-api
```

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
