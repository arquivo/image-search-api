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
