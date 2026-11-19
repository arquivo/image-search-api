package pt.arquivo;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
@PropertySource("classpath:servlet.properties")
public class ImageSearchApplication {

    @Value("${solr.server}")
    private String solrServer;

    @Value("${solr.collection}")
    private String solrCollection;

    @Value("${wayback.host}")
    private String waybackHost;

    @Value("${solr.timeallowed.ms}")
    private String solrTimeAllowedMs;

    // Tomcat's worker thread cap bounds how many Solr queries can be in flight at once.
    @Value("${server.tomcat.threads.max:200}")
    private String tomcatMaxThreads;

    public static void main(String[] args) {
        SpringApplication.run(ImageSearchApplication.class, args);
    }

    @Bean
    public ServletRegistrationBean<ImageSearchServlet> imageSearchServlet() {
        ServletRegistrationBean<ImageSearchServlet> registration =
                new ServletRegistrationBean<>(new ImageSearchServlet(), "/*");
        registration.addInitParameter("solrServer", solrServer);
        registration.addInitParameter("solrCollection", solrCollection);
        registration.addInitParameter("waybackHost", waybackHost);
        registration.addInitParameter("solrTimeAllowedMs", solrTimeAllowedMs);
        registration.addInitParameter("solrMaxConnectionsPerHost", tomcatMaxThreads);
        return registration;
    }

    /**
     * Dedicated to the /imagesearch/healthcheck endpoint, so it never shares state or timeouts with the
     * SolrClient ImageSearchServlet uses to serve queries. Timeouts are explicit and comparatively short,
     * so a Solr that's up but hanging fails the healthcheck quickly instead of blocking the deploy gate
     * that calls it.
     */
    @Bean
    SolrClient healthCheckSolrClient(
            @Value("${healthcheck.solr.connectiontimeout.ms:2000}") int connectionTimeoutMillis,
            @Value("${healthcheck.solr.sockettimeout.ms:3000}") int socketTimeoutMillis) {
        return new Http2SolrClient.Builder(solrServer + solrCollection)
                .useHttp1_1(true)
                .withConnectionTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    @Bean
    public ServletRegistrationBean<HealthCheckServlet> healthCheckServlet(SolrClient healthCheckSolrClient) {
        HealthCheckServlet servlet = new HealthCheckServlet();
        servlet.setSolrClient(healthCheckSolrClient);
        return new ServletRegistrationBean<>(servlet, "/imagesearch/healthcheck");
    }
}
