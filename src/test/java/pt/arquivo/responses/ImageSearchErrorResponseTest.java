package pt.arquivo.responses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchErrorResponseTest {

    @Test
    void constructorSetsDefaultFieldsAndErrorMapEntry() {
        Exception e = new IllegalStateException("boom");

        ImageSearchErrorResponse response = new ImageSearchErrorResponse(e);

        assertEquals("Arquivo.pt - image search service.", response.serviceName);
        assertEquals("https://arquivo.pt/images.jsp", response.linkToService);
        assertEquals("https://github.com/arquivo/pwa-technologies/wiki/ImageSearch-API-v1-(beta)",
                response.linkToDocumentation);
        assertEquals(0, response.totalItems);
        assertEquals(0, response.numberOfResponseItems);
        assertEquals(0, response.offset);
        assertTrue(response.responseItems.isEmpty());

        assertEquals("boom", response.error.get("java.lang.IllegalStateException"));
    }

    @Test
    void errorMapHandlesExceptionWithNullMessage() {
        Exception e = new RuntimeException();

        ImageSearchErrorResponse response = new ImageSearchErrorResponse(e);

        assertTrue(response.error.containsKey("java.lang.RuntimeException"));
        assertNull(response.error.get("java.lang.RuntimeException"));
    }
}
