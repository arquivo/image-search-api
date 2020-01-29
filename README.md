# ImageSearchAPI
Web app that acts as an API proxy of Apache Solr taht contains web archived images.

## Build

It produces a war file to be run on a Java servlet web server like Apache Tomcat.

```bash
mvn clean verify
```

## Development

To make development more rapid there is a docker-compose.yml file that runs the web application inside a docker.

Example run

```bash
mvn clean verify && docker-compose up
```

Example with custom solr server:

```bash
mvn clean verify -Dsolr.url=p51.arquivo.pt && docker-compose up
```
