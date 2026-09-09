package pt.arquivo;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HealthCheckServletTest {

    @Mock
    private SolrClient solrClient;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private HealthCheckServlet servlet;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new HealthCheckServlet();
        servlet.setSolrClient(solrClient);

        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void healthcheckSolrReachable() throws Exception {
        lenient().when(solrClient.ping()).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertEquals("{\"solr\":\"ok\"}", responseBody.toString().trim());
    }

    @Test
    void healthcheckSolrUnreachableOnSolrServerException() throws Exception {
        lenient().when(solrClient.ping())
                .thenThrow(new SolrServerException("Connection refused to solr-internal.arquivo.pt:8983"));

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertEquals("{\"solr\":\"unreachable\"}", responseBody.toString().trim());
    }

    @Test
    void healthcheckSolrUnreachableOnIOException() throws Exception {
        lenient().when(solrClient.ping()).thenThrow(new IOException("timeout"));

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertEquals("{\"solr\":\"unreachable\"}", responseBody.toString().trim());
    }
}
