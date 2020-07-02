# Use maven container to build and tomcat container to run.
# 1. container to build
FROM maven:3.5.2-jdk-8-alpine AS MAVEN_TOOL_CHAIN
#
# maven pom.xml
COPY pom.xml /tmp/build/pom.xml
# copy source code
COPY src /tmp/build/src
# 
WORKDIR /tmp/build
# build
RUN mvn verify

# 2. container to run
FROM tomcat:9.0.36-jdk11-openjdk
COPY --from=MAVEN_TOOL_CHAIN /tmp/build/target/image-search-api.war $CATALINA_HOME/webapps/ROOT.war

HEALTHCHECK --interval=1m --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost:8080/ROOT/ || exit 1
