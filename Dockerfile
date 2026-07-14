FROM maven:3.8-openjdk-11 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -B -DskipTests

FROM tomcat:9-jdk11
COPY --from=build /app/target/image-search-api /usr/local/tomcat/webapps/ROOT
COPY lib/additional_catalina_params.txt /usr/local/tomcat/bin/setenv.sh
RUN chmod 755 /usr/local/tomcat/bin/setenv.sh
EXPOSE 8080
