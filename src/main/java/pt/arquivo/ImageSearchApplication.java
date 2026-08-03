package pt.arquivo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpath:servlet.properties")
public class ImageSearchApplication extends SpringBootServletInitializer {

    @Value("${solr.server}")
    private String solrServer;

    @Value("${solr.collection}")
    private String solrCollection;

    @Value("${wayback.host}")
    private String waybackHost;

    public static void main(String[] args) {
        // log4j.properties resolves ${catalina.home} for its log file path; the embedded
        // Tomcat never sets it, so default it here before Spring/log4j initialize, or the
        // path collapses to "/logs/..." at the filesystem root.
        System.setProperty("catalina.home", System.getProperty("catalina.home", System.getProperty("user.dir")));
        SpringApplication.run(ImageSearchApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ImageSearchApplication.class);
    }

    @Bean
    public ServletRegistrationBean<ImageSearchServlet> imageSearchServlet() {
        ServletRegistrationBean<ImageSearchServlet> registration =
                new ServletRegistrationBean<>(new ImageSearchServlet(), "/*");
        registration.addInitParameter("solrServer", solrServer);
        registration.addInitParameter("solrCollection", solrCollection);
        registration.addInitParameter("waybackHost", waybackHost);
        return registration;
    }
}
