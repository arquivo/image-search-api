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

### Concurrency limit: Tomcat's connection and thread pool

Independently of container memory, Spring Boot's embedded Tomcat (9.0.x, via the 2.7.18 parent) caps concurrent traffic with three unmodified defaults that stack on top of each other, not one:

| Property | Default | What it actually limits |
|---|---|---|
| `server.tomcat.threads.max` | 200 | Worker threads — how many requests can be **actively processed** at once. This is the real backstop, since every other layer is just waiting on this one. |
| `server.tomcat.max-connections` | 8192 | Open sockets Tomcat's NIO poller will accept and hold at once, including idle keep-alive connections that aren't currently being processed. Connections beyond the 200 processing threads don't get refused here — they just sit in the poller until a thread frees up. |
| `server.tomcat.accept-count` | 100 | OS-level TCP backlog for connection attempts arriving after `max-connections` is already full. Only kicks in once 8192 connections are already open, which requires far more concurrent clients than this app has ever been load tested against. Beyond this, the OS refuses/resets new connections outright. |

In practice, for this app's traffic pattern the ceiling that matters is `threads.max=200`: with only ~150-250 concurrent requests exercised in load testing (see memory table above), `max-connections` and `accept-count` have never come close to being the binding constraint. Raising the memory limit alone doesn't increase how many requests can be served concurrently — it only gives the JVM more room to run at the existing 200-thread ceiling.

To raise the ceiling, pass the equivalent Spring Boot properties as JVM system properties in `JAVA_OPTS` (or as `SERVER_TOMCAT_THREADS_MAX`/`SERVER_TOMCAT_MAX_CONNECTIONS`/`SERVER_TOMCAT_ACCEPT_COUNT` environment variables, via Spring's relaxed env binding):

```bash
docker run -p 8080:8080 --memory=4g \
  -e JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Dserver.tomcat.threads.max=400 -Dserver.tomcat.accept-count=200" \
  image-search-api
```

Raising the thread cap increases the number of requests that can be in flight at once, and each one holds its own thread stack and request/response buffers — so it also raises the memory the JVM can actually use under load. Re-run load testing at the new thread count before increasing it in production, and scale the memory limit up alongside it rather than in isolation. `max-connections` (8192) is already well above `threads.max`, so it rarely needs raising in lockstep — only revisit it if load testing shows connections queuing in the poller before threads are saturated (e.g. many slow/idle clients holding keep-alive connections open).

### Timeouts

Also unmodified from Spring Boot's defaults:

| Property | Default | Behavior |
|---|---|---|
| `server.tomcat.connection-timeout` | 60000ms (60s) | How long the connector waits, after accepting a connection, for the client to send the request line/headers. Protects against slow-loris-style clients that open a connection and trickle bytes, tying up a poller slot indefinitely. |
| `server.tomcat.keep-alive-timeout` | Falls back to `connection-timeout` (60s) | How long an idle keep-alive connection can sit between requests before Tomcat closes it. Matters directly for the `max-connections` ceiling above: a lower value frees up poller slots faster under high client churn, at the cost of more TCP/TLS handshakes for clients that would otherwise reuse the connection. |
| `server.tomcat.max-keep-alive-requests` | 100 | Number of requests a single keep-alive connection can serve before Tomcat closes it and forces a new connection. Exists to bound how long one connection can monopolize a poller slot, and to spread load evenly if requests get routed through a load balancer with per-connection stickiness. |

None of these interact with the Solr query itself — there's no explicit HTTP client timeout configured against `p44.arquivo.pt` (see `waybackAddress`/`solr.server` in `pom.xml`), so a slow or hung Solr response can hold a Tomcat worker thread for as long as the underlying socket read takes, which is a more direct way to exhaust the 200-thread pool than any of the connector-level settings above.

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
