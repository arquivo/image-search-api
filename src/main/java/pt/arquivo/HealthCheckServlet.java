package pt.arquivo;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;

/**
 * Only checks Solr connectivity: it exists to gate rolling deploys of this service, pinging a
 * dedicated SolrClient (see ImageSearchApplication) that's independent of the one ImageSearchServlet
 * uses to serve queries.
 */
public class HealthCheckServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckServlet.class);
    private static final Gson GSON = new Gson();

    private SolrClient solr;

    // Package-private seam so tests can inject a mock SolrClient without going through Spring wiring.
    void setSolrClient(SolrClient solr) {
        this.solr = solr;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // RuntimeException too: SolrJ's Jetty-based client throws IllegalArgumentException for a Solr URL it can't
        // parse, e.g. a ZooKeeper host list, so catch it to answer 503 rather than a 500 page.
        try {
            solr.ping();
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(GSON.toJson(Collections.singletonMap("solr", "ok")));
        } catch (SolrServerException | IOException | RuntimeException e) {
            LOG.error("Solr healthcheck failed: ", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().println(GSON.toJson(Collections.singletonMap("solr", "unreachable")));
        }
    }
}
