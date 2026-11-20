package pt.arquivo;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApiDocsServletTest {

    private static OpenAPI spec;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private ApiDocsServlet servlet;
    private ByteArrayOutputStream body;

    @BeforeAll
    static void parseSpec() throws Exception {
        String json = new String(ApiDocsServlet.loadSpecJson(), StandardCharsets.UTF_8);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(json);
        assertEquals(List.of(), result.getMessages());
        spec = result.getOpenAPI();
    }

    @BeforeEach
    void setUp() throws Exception {
        servlet = new ApiDocsServlet();
        servlet.init();
        body = new ByteArrayOutputStream();
        lenient().when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                body.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }
        });
        lenient().when(request.getContextPath()).thenReturn("");
        lenient().when(request.getServletPath()).thenReturn("/imagesearch/api-docs");
    }

    private void get(String pathInfo) throws Exception {
        lenient().when(request.getPathInfo()).thenReturn(pathInfo);
        servlet.doGet(request, response);
    }

    @Test
    void specDocumentsTheSearchAndHealthCheckEndpoints() {
        assertEquals(Set.of("/imagesearch", "/imagesearch/healthcheck"), spec.getPaths().keySet());
    }

    @Test
    void specDocumentsEverySearchParameterTheServletReads() {
        Set<String> documented = spec.getPaths().get("/imagesearch").getGet().getParameters().stream()
                .map(Parameter::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("q", "from", "to", "type", "size", "offset", "maxItems", "siteSearch", "collection",
                "safeSearch", "sort", "fields", "more", "prettyPrint"), documented);
    }

    // YAML 1.1 reads an unquoted on/off as a boolean
    @Test
    void safeSearchValuesStayStrings() {
        Parameter safeSearch = spec.getPaths().get("/imagesearch").getGet().getParameters().stream()
                .filter(parameter -> parameter.getName().equals("safeSearch"))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("on", "off"), safeSearch.getSchema().getEnum());
        assertEquals("on", safeSearch.getSchema().getDefault());
    }

    @Test
    void redirectsToTheTrailingSlashSoRelativeFilesResolve() throws Exception {
        get(null);

        verify(response).sendRedirect("/imagesearch/api-docs/");
    }

    @Test
    void servesTheSpecAsJson() throws Exception {
        get("/v3");

        verify(response).setContentType("application/json");
        verify(response).setHeader("Access-Control-Allow-Origin", "*");
        assertTrue(body.toString(StandardCharsets.UTF_8).startsWith("{\"openapi\":\"3.0.3\""));
    }

    @Test
    void servesTheSwaggerUiPageWithAContentSecurityPolicy() throws Exception {
        get("/");

        verify(response).setContentType("text/html;charset=UTF-8");
        verify(response).setHeader("Content-Security-Policy", ApiDocsServlet.CONTENT_SECURITY_POLICY);
        assertTrue(body.toString(StandardCharsets.UTF_8).contains("swagger-ui-bundle.js"));
    }

    @Test
    void servesOwnSwaggerInitializerPointingAtTheSpec() throws Exception {
        get("/swagger-initializer.js");

        assertTrue(body.toString(StandardCharsets.UTF_8).contains("url: \"v3\""));
    }

    @Test
    void servesSwaggerUiFilesFromTheWebjar() throws Exception {
        get("/swagger-ui-bundle.js");

        verify(response).setContentType("text/javascript;charset=UTF-8");
        assertTrue(body.size() > 100_000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/oauth2-redirect.html", "/index.html", "/../openapi.yaml", "/missing.js", "/v3/x"})
    void answers404ForAnythingElse(String pathInfo) throws Exception {
        get(pathInfo);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
        verify(response, never()).getOutputStream();
    }
}
