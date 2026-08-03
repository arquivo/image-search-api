FROM maven:3.8-eclipse-temurin-8 AS build
WORKDIR /app
ARG SOLR_SERVER
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -B -DskipTests ${SOLR_SERVER:+-Dsolr.server=${SOLR_SERVER}}

FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=build /app/target/image-search-api.war app.war

# Default JVM flags: size the heap off the container's memory limit (not the
# host's) so behavior stays consistent across environments, use G1 for more
# predictable pause times than JDK 8's default Parallel GC, and fail fast on
# OOM so the container exits and can be restarted by the orchestrator instead
# of limping along. Override JAVA_OPTS entirely (e.g. to add
# -agentlib:jdwp=... for remote debugging) when running the image.
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.war"]
