package pt.arquivo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchApplicationTest {

    // Spring Boot quietly starts without a web server when a servlet dependency is missing, so check the
    // packaged dependencies actually give the app an embedded Tomcat. No Solr is needed: the clients only
    // connect on the first request.
    @Test
    void startsWithEmbeddedWebServer() {
        try (ConfigurableApplicationContext context = SpringApplication.run(ImageSearchApplication.class,
                "--server.port=0", "--solr.server=http://127.0.0.1:1/solr/")) {
            assertTrue(context instanceof WebServerApplicationContext);
            assertTrue(((WebServerApplicationContext) context).getWebServer().getPort() > 0);
        }
    }
}
