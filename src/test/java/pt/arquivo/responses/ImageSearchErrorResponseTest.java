package pt.arquivo.responses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchErrorResponseTest {

    @Test
    void constructorSetsDefaultFieldsAndGenericError() {
        ImageSearchErrorResponse response = new ImageSearchErrorResponse();

        assertEquals("Arquivo.pt - image search service.", response.serviceName);
        assertEquals("https://arquivo.pt/images.jsp", response.linkToService);
        assertEquals("https://github.com/arquivo/pwa-technologies/wiki/ImageSearch-API-v1-(beta)",
                response.linkToDocumentation);
        assertEquals(0, response.totalItems);
        assertEquals(0, response.numberOfResponseItems);
        assertEquals(0, response.offset);
        assertTrue(response.responseItems.isEmpty());

        assertEquals("The search failed.", response.error);
    }
}
