package pt.arquivo;

import com.google.gson.GsonBuilder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Serves the OpenAPI 3 description of this API under /imagesearch/api-docs/v3, and Swagger UI to browse it under
 * /imagesearch/api-docs. The search is a plain servlet rather than Spring MVC controllers, so there's nothing to
 * generate the description from: it's written by hand in api-docs/openapi.yaml.
 */
public class ApiDocsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    static final String SPEC_RESOURCE = "api-docs/openapi.yaml";
    private static final String SWAGGER_UI_POM_PROPERTIES = "META-INF/maven/org.webjars/swagger-ui/pom.properties";

    // Served from this app's api-docs resources, by request path
    private static final Map<String, String> OWN_FILES = Map.of(
            "/", "api-docs/index.html",
            "/swagger-initializer.js", "api-docs/swagger-initializer.js");

    // Served from the Swagger UI webjar. An allowlist, so no part of the request path reaches the classpath lookup.
    private static final Set<String> SWAGGER_UI_FILES = Set.of(
            "swagger-ui.css", "swagger-ui-bundle.js", "favicon-16x16.png", "favicon-32x32.png");

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html;charset=UTF-8",
            "js", "text/javascript;charset=UTF-8",
            "css", "text/css;charset=UTF-8",
            "png", "image/png");

    // Swagger UI sets inline styles and uses data: images; every script comes from this server.
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'";

    private byte[] specJson;
    private String swaggerUiRoot;

    @Override
    public void init() throws ServletException {
        specJson = loadSpecJson();
        swaggerUiRoot = "META-INF/resources/webjars/swagger-ui/" + swaggerUiVersion() + "/";
    }

    // YAML is easier to write and review than JSON, but clients expect JSON, so convert it once at startup.
    static byte[] loadSpecJson() throws ServletException {
        try (InputStream in = resource(SPEC_RESOURCE)) {
            Object spec = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            return new GsonBuilder().disableHtmlEscaping().create().toJson(spec).getBytes(StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            throw new ServletException("Can't load " + SPEC_RESOURCE, e);
        }
    }

    private static String swaggerUiVersion() throws ServletException {
        try (InputStream in = resource(SWAGGER_UI_POM_PROPERTIES)) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version");
        } catch (IOException e) {
            throw new ServletException("Can't find the Swagger UI webjar", e);
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream in = ApiDocsServlet.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException(name + " not found");
        }
        return in;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null) {
            // Swagger UI's files are loaded relative to the page, so the page needs the trailing slash
            response.sendRedirect(request.getContextPath() + request.getServletPath() + "/");
            return;
        }

        response.setHeader("X-Content-Type-Options", "nosniff");
        if (path.equals("/v3")) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getOutputStream().write(specJson);
        } else if (OWN_FILES.containsKey(path)) {
            serve(OWN_FILES.get(path), response);
        } else if (SWAGGER_UI_FILES.contains(path.substring(1))) {
            serve(swaggerUiRoot + path.substring(1), response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private static void serve(String resourceName, HttpServletResponse response) throws IOException {
        String extension = resourceName.substring(resourceName.lastIndexOf('.') + 1);
        response.setContentType(CONTENT_TYPES.get(extension));
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        try (InputStream in = resource(resourceName)) {
            in.transferTo(response.getOutputStream());
        }
    }
}
