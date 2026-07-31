package pt.arquivo;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ImageSearchResultsTest {

    private static ImageSearchResults build(String[] requestedFields, long totalItems, int numberOfResponseItems,
                                             SolrDocumentList responseItems) {
        return new ImageSearchResults(requestedFields, totalItems, numberOfResponseItems, 0,
                "", "", "", responseItems, false);
    }

    @Test
    void numberOfResponseItemsIsClampedToTotalItemsWhenSmaller() {
        SolrDocumentList docs = new SolrDocumentList();
        docs.add(new SolrDocument());

        ImageSearchResults results = build(new String[]{}, 0, 5, docs);

        assertEquals(0, results.numberOfResponseItems);
    }

    @Test
    void numberOfResponseItemsIsUnchangedWhenNotGreaterThanTotalItems() {
        SolrDocumentList docs = new SolrDocumentList();
        docs.add(new SolrDocument());

        ImageSearchResults results = build(new String[]{}, 5, 1, docs);

        assertEquals(1, results.numberOfResponseItems);
    }

    @Test
    void safeFieldIsInvertedWhenMapped() {
        SolrDocument doc = new SolrDocument();
        doc.addField("safe", 0.2f);

        SolrDocumentList docs = new SolrDocumentList();
        docs.add(doc);

        ImageSearchResults results = build(new String[]{"safe"}, 1, 1, docs);

        SolrDocument mapped = results.responseItems.get(0);
        assertEquals(0.8f, (float) mapped.getFieldValue("safe"), 0.0001f);
    }

    @Test
    void imgLinkToArchiveIsOmittedWhenImgUrlFieldIsMissing() throws Exception {
        SimpleDateFormat v1 = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        Date timestamp = v1.parse("20200115120000");

        SolrDocument doc = new SolrDocument();
        doc.addField("imgCrawlTimestamp", timestamp);
        // imgUrl deliberately absent

        SolrDocumentList docs = new SolrDocumentList();
        docs.add(doc);

        ImageSearchResults results = build(new String[]{"imgLinkToArchive"}, 1, 1, docs);

        SolrDocument mapped = results.responseItems.get(0);
        assertFalse(mapped.containsKey("imgLinkToArchive"));
    }

    @Test
    void pageLinkToArchiveIsOmittedWhenPageCrawlTimestampFieldIsMissing() {
        SolrDocument doc = new SolrDocument();
        doc.addField("pageUrl", "http://example.com/page.html");
        // pageCrawlTimestamp deliberately absent

        SolrDocumentList docs = new SolrDocumentList();
        docs.add(doc);

        ImageSearchResults results = build(new String[]{"pageLinkToArchive"}, 1, 1, docs);

        SolrDocument mapped = results.responseItems.get(0);
        assertFalse(mapped.containsKey("pageLinkToArchive"));
    }

    @Test
    void imgLinkToArchiveIsBuiltWhenBothFieldsPresent() throws Exception {
        SimpleDateFormat v1 = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        Date timestamp = v1.parse("20200115120000");

        SolrDocument doc = new SolrDocument();
        doc.addField("imgCrawlTimestamp", timestamp);
        doc.addField("imgUrl", "http://example.com/img.jpg");

        SolrDocumentList docs = new SolrDocumentList();
        docs.add(doc);

        ImageSearchResults results = build(new String[]{"imgLinkToArchive"}, 1, 1, docs);

        SolrDocument mapped = results.responseItems.get(0);
        String expectedWayback = ImageSearchProperties.get("waybackAddress");
        assertEquals(expectedWayback + "20200115120000im_/http://example.com/img.jpg",
                mapped.getFieldValue("imgLinkToArchive"));
    }
}
