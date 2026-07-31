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

# Extra JVM flags at runtime, e.g. -agentlib:jdwp=... for remote debugging
ENV JAVA_OPTS=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.war"]
