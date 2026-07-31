package pt.arquivo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageSearchServletTest {

    @Mock
    private SolrClient solrClient;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private QueryResponse queryResponse;

    private ImageSearchServlet servlet;
    private StringWriter responseBody;
    private final Map<String, String> params = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        servlet = new ImageSearchServlet();
        servlet.setSolrClient(solrClient);

        params.clear();
        lenient().when(request.getParameter(anyString()))
                .thenAnswer(invocation -> params.get(invocation.getArgument(0, String.class)));

        lenient().when(request.getScheme()).thenReturn("http");
        lenient().when(request.getServerName()).thenReturn("localhost");
        lenient().when(request.getServerPort()).thenReturn(80);
        lenient().when(request.getRequestURI()).thenReturn("/imagesearch");
        lenient().when(request.getQueryString()).thenReturn(null);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        lenient().when(queryResponse.getResults()).thenReturn(new SolrDocumentList());
        lenient().when(queryResponse.getResponseHeader()).thenReturn(new NamedList<>());
        lenient().when(solrClient.query(any(SolrQuery.class))).thenReturn(queryResponse);
    }

    private SolrQuery runAndCaptureSolrQuery() throws Exception {
        servlet.doGet(request, response);
        ArgumentCaptor<SolrQuery> captor = ArgumentCaptor.forClass(SolrQuery.class);
        verify(solrClient).query(captor.capture());
        return captor.getValue();
    }

    private JsonObject runAndParseJsonResponse() throws Exception {
        servlet.doGet(request, response);
        return new JsonParser().parse(responseBody.toString()).getAsJsonObject();
    }

    private static List<String> filterQueries(SolrQuery solrQuery) {
        return Arrays.asList(solrQuery.getFilterQueries());
    }

    // ---------------------------------------------------------------
    // init(ServletConfig) tests
    // ---------------------------------------------------------------

    private static ServletConfig servletConfigWith(String waybackHost, String solrServer, String solrCollection) {
        ServletConfig config = org.mockito.Mockito.mock(ServletConfig.class);
        lenient().when(config.getInitParameter("waybackHost")).thenReturn(waybackHost);
        lenient().when(config.getInitParameter("solrServer")).thenReturn(solrServer);
        lenient().when(config.getInitParameter("solrCollection")).thenReturn(solrCollection);
        return config;
    }

    @Test
    void initThrowsServletExceptionWhenSolrCollectionIsMissing() {
        ServletConfig config = servletConfigWith("https://wayback.example.com/", "http://solr.example.com/solr/", null);

        ImageSearchServlet freshServlet = new ImageSearchServlet();
        assertThrows(ServletException.class, () -> freshServlet.init(config));
    }

    @Test
    void initDoesNotThrowWhenWaybackHostIsMissing() {
        // Only waybackHost is truly optional here: it's only used for LOG.debug.
        // solrServer being null would NPE inside createSolr(), so it's deliberately left set.
        ServletConfig config = servletConfigWith(null, "http://solr.example.com/solr/", "imagesearch");

        ImageSearchServlet freshServlet = new ImageSearchServlet();
        assertDoesNotThrow(() -> freshServlet.init(config));
    }

    // ---------------------------------------------------------------
    // Request-building tests: assert what we actually send to Solr
    // ---------------------------------------------------------------

    @Test
    void defaultRequestUsesMatchAllQueryAndDefaultFilters() throws Exception {
        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("*:*", solrQuery.getQuery());
        assertEquals(Integer.valueOf(50), solrQuery.getRows());
        assertEquals(Integer.valueOf(0), solrQuery.getStart());
        assertEquals("edismax", solrQuery.get("defType"));

        List<String> fq = filterQueries(solrQuery);
        assertTrue(fq.contains("blocked:0"));
        assertTrue(fq.contains("isInline:false"));
        assertTrue(fq.contains("safe:[0 TO 0.49]"));

        List<SolrQuery.SortClause> sorts = solrQuery.getSorts();
        assertEquals(Arrays.asList(
                new SolrQuery.SortClause("score", SolrQuery.ORDER.desc),
                new SolrQuery.SortClause("imgCrawlTimestamp", SolrQuery.ORDER.asc),
                new SolrQuery.SortClause("imgUrl", SolrQuery.ORDER.asc)
        ), sorts);
    }

    @Test
    void customQueryAndPagingParamsAreForwardedToSolr() throws Exception {
        params.put("q", "cats");
        params.put("offset", "10");
        params.put("maxItems", "20");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertEquals(Integer.valueOf(10), solrQuery.getStart());
        assertEquals(Integer.valueOf(20), solrQuery.getRows());
    }

    @Test
    void maxItemsAboveTwoHundredIsClampedToTwoHundred() throws Exception {
        params.put("maxItems", "300");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals(Integer.valueOf(200), solrQuery.getRows());
    }

    @Test
    void negativeMaxItemsIsClampedToZero() throws Exception {
        params.put("maxItems", "-5");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals(Integer.valueOf(0), solrQuery.getRows());
    }

    @Test
    void nonNumericMaxItemsFallsBackToDefault() throws Exception {
        params.put("maxItems", "abc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals(Integer.valueOf(50), solrQuery.getRows());
    }

    @Test
    void nonNumericOffsetFallsBackToDefault() throws Exception {
        params.put("offset", "abc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals(Integer.valueOf(0), solrQuery.getStart());
    }

    @Test
    void dateRangeFilterUsesTranslatedV2Dates() throws Exception {
        params.put("from", "20200115120000");
        params.put("to", "20211231235959");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        SimpleDateFormat v1 = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        SimpleDateFormat v2 = (SimpleDateFormat) APIVersionTranslator.V2_DATE_FORMAT.clone();
        String expectedFrom = v2.format(v1.parse("20200115120000"));
        String expectedTo = v2.format(v1.parse("20211231235959"));

        assertTrue(filterQueries(solrQuery).contains(
                "imgCrawlTimestamp:[" + expectedFrom + " TO " + expectedTo + "]"));
    }

    @Test
    void onlyFromDateDefaultsToEndOfCurrentYear() throws Exception {
        params.put("from", "20200115120000");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        SimpleDateFormat v1 = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        SimpleDateFormat v2 = (SimpleDateFormat) APIVersionTranslator.V2_DATE_FORMAT.clone();
        String expectedFrom = v2.format(v1.parse("20200115120000"));

        assertTrue(filterQueries(solrQuery).contains(
                "imgCrawlTimestamp:[" + expectedFrom + " TO " + endOfCurrentYearV2() + "]"));
    }

    @Test
    void onlyToDateDefaultsFromToNineteenNinetySix() throws Exception {
        params.put("to", "20211231235959");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        SimpleDateFormat v1 = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        SimpleDateFormat v2 = (SimpleDateFormat) APIVersionTranslator.V2_DATE_FORMAT.clone();
        String expectedTo = v2.format(v1.parse("20211231235959"));

        assertTrue(filterQueries(solrQuery).contains(
                "imgCrawlTimestamp:[1996-01-01T00:00:00Z TO " + expectedTo + "]"));
    }

    private static String endOfCurrentYearV2() {
        Calendar endOfYear = new GregorianCalendar();
        endOfYear.set(Calendar.MONTH, Calendar.DECEMBER);
        endOfYear.set(Calendar.DAY_OF_MONTH, 31);
        endOfYear.set(Calendar.HOUR_OF_DAY, 23);
        endOfYear.set(Calendar.MINUTE, 59);
        endOfYear.set(Calendar.SECOND, 59);
        endOfYear.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat v2 = (SimpleDateFormat) APIVersionTranslator.V2_DATE_FORMAT.clone();
        return v2.format(endOfYear.getTime());
    }

    @Test
    void safeSearchOffRemovesDefaultSafeFilter() throws Exception {
        params.put("safeSearch", "off");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertFalse(filterQueries(solrQuery).stream().anyMatch(f -> f.startsWith("safe:")));
    }

    @Test
    void mimeTypeFilterTranslatesJpgToJpegAndJoinsWithOr() throws Exception {
        params.put("type", "jpg,png");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertTrue(filterQueries(solrQuery).contains("imgMimeType: image/jpeg OR imgMimeType: image/png"));
    }

    @Test
    void sizeFilterBuildsFrangeQueryForSmall() throws Exception {
        params.put("size", "sm");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertTrue(filterQueries(solrQuery).contains("{!frange u=65536 }product(imgHeight,imgWidth)"));
    }

    @Test
    void sizeFilterBuildsFrangeQueryForMedium() throws Exception {
        params.put("size", "md");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertTrue(filterQueries(solrQuery).contains("{!frange l=65537 u=810000 }product(imgHeight,imgWidth)"));
    }

    @Test
    void sizeFilterBuildsFrangeQueryForLarge() throws Exception {
        params.put("size", "lg");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertTrue(filterQueries(solrQuery).contains("{!frange l=810001}product(imgHeight,imgWidth)"));
    }

    @Test
    void siteSearchBuildsPageHostFilter() throws Exception {
        params.put("siteSearch", "example.com");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertTrue(filterQueries(solrQuery).contains("(pageHost:example.com OR pageHost:www.example.com)"));
    }

    @Test
    void emptySiteSearchAddsNoFilterQuery() throws Exception {
        params.put("siteSearch", "");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertFalse(filterQueries(solrQuery).contains(""));
    }

    @Test
    void collectionFilterJoinsMultipleCollectionsWithOr() throws Exception {
        params.put("collection", "foo,bar");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertTrue(filterQueries(solrQuery).contains("collection:foo OR collection:bar"));
    }

    @Test
    void inlineQueryOperatorsAreParsedIntoFiltersAndSortInsteadOfQuery() throws Exception {
        params.put("q", "cats site:example.com type:png safe:off size:sm sort:imgCrawlTimestamp,desc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());

        List<String> fq = filterQueries(solrQuery);
        assertTrue(fq.contains("(pageHost:example.com OR pageHost:www.example.com)"));
        assertTrue(fq.contains("imgMimeType: image/png"));
        assertTrue(fq.contains("{!frange u=65536 }product(imgHeight,imgWidth)"));
        assertFalse(fq.stream().anyMatch(f -> f.startsWith("safe:")));

        assertEquals(
                Arrays.asList(new SolrQuery.SortClause("imgCrawlTimestamp", SolrQuery.ORDER.desc)),
                solrQuery.getSorts());
    }

    @Test
    void fieldsParamIsTranslatedToV2AndAlwaysIncludesMandatoryFields() throws Exception {
        params.put("fields", "imgDigest");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("id,imgUrl,imgCrawlTimestamp,pageUrl,pageCrawlTimestamp,", solrQuery.get("fl"));
    }

    @Test
    void moreParamAppendsAdditionalFieldsToRequestedFl() throws Exception {
        params.put("more", "pageHost,safe");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        String fl = solrQuery.get("fl");
        assertTrue(fl.contains("pageHost,"));
        assertTrue(fl.contains("safe,"));
    }

    @Test
    void fqOperatorInQueryReplacesMatchingFilterAndAddsNew() throws Exception {
        params.put("q", "cats fq:blocked:1");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        List<String> fq = filterQueries(solrQuery);
        assertFalse(fq.contains("blocked:0"));
        assertTrue(fq.contains("blocked:1"));
    }

    @Test
    void fqOperatorWithMultipleSemicolonSeparatedTokensAndUnderscoreUnescaping() throws Exception {
        params.put("q", "cats fq:blocked:1;pageHost:example_com");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        List<String> fq = filterQueries(solrQuery);
        assertFalse(fq.contains("blocked:0"));
        assertTrue(fq.contains("blocked:1"));
        assertTrue(fq.contains("pageHost:example com"));
    }

    @Test
    void allInlineOperatorsCombinedInASingleQueryAreAllParsedCorrectly() throws Exception {
        params.put("q", "cats site:example.com type:png safe:off size:sm sort:imgCrawlTimestamp,desc "
                + "fq:collection:foo collapse:imgDigest");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());

        List<String> fq = filterQueries(solrQuery);
        assertTrue(fq.contains("(pageHost:example.com OR pageHost:www.example.com)"));
        assertTrue(fq.contains("imgMimeType: image/png"));
        assertTrue(fq.contains("{!frange u=65536 }product(imgHeight,imgWidth)"));
        assertFalse(fq.stream().anyMatch(f -> f.startsWith("safe:")));
        assertTrue(fq.contains("collection:foo"));
        assertTrue(fq.contains("{!collapse field=imgDigest}"));

        assertEquals(
                Arrays.asList(new SolrQuery.SortClause("imgCrawlTimestamp", SolrQuery.ORDER.desc)),
                solrQuery.getSorts());
    }

    @Test
    void inlineSafeOffRemovesSafeFilterEntirely() throws Exception {
        params.put("q", "cats safe:off");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertFalse(filterQueries(solrQuery).stream().anyMatch(f -> f.startsWith("safe:")));
    }

    @Test
    void inlineSafeOnLeavesExactlyOneSafeFilterNotDuplicated() throws Exception {
        params.put("q", "cats safe:on");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        List<String> safeFilters = filterQueries(solrQuery).stream()
                .filter(f -> f.startsWith("safe:"))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(Arrays.asList("safe:[0 TO 0.49]"), safeFilters);
    }

    @Test
    void collapseOperatorInQueryAddsCollapseFilter() throws Exception {
        params.put("q", "cats collapse:imgDigest");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertTrue(filterQueries(solrQuery).contains("{!collapse field=imgDigest}"));
    }

    @Test
    void sortOperatorWithCaretAppliesPowTransform() throws Exception {
        params.put("q", "cats sort:imgWidth^2,desc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertEquals(
                Arrays.asList(new SolrQuery.SortClause("pow(imgWidth,2)", SolrQuery.ORDER.desc)),
                solrQuery.getSorts());
    }

    @Test
    void sortOperatorWithAsteriskAppliesProductTransform() throws Exception {
        params.put("q", "cats sort:imgWidth*imgHeight,asc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertEquals(
                Arrays.asList(new SolrQuery.SortClause("product(imgWidth,imgHeight)", SolrQuery.ORDER.asc)),
                solrQuery.getSorts());
    }

    @Test
    void sortOperatorWithSlashAppliesDivTransform() throws Exception {
        params.put("q", "cats sort:imgWidth/imgHeight,asc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertEquals(
                Arrays.asList(new SolrQuery.SortClause("div(imgWidth,imgHeight)", SolrQuery.ORDER.asc)),
                solrQuery.getSorts());
    }

    @Test
    void sortOperatorWithPlusAppliesSumTransform() throws Exception {
        params.put("q", "cats sort:imgWidth+imgHeight,asc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertEquals(
                Arrays.asList(new SolrQuery.SortClause("sum(imgWidth,imgHeight)", SolrQuery.ORDER.asc)),
                solrQuery.getSorts());
    }

    @Test
    void sortOperatorWithMinusAppliesSubTransform() throws Exception {
        params.put("q", "cats sort:imgWidth-imgHeight,asc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertEquals(
                Arrays.asList(new SolrQuery.SortClause("sub(imgWidth,imgHeight)", SolrQuery.ORDER.asc)),
                solrQuery.getSorts());
    }

    @Test
    void sortOperatorWithMultipleSemicolonSeparatedInstancesAppliesEachClause() throws Exception {
        params.put("q", "cats sort:imgCrawlTimestamp,desc;imgWidth,asc");

        SolrQuery solrQuery = runAndCaptureSolrQuery();

        assertEquals("cats", solrQuery.getQuery());
        assertEquals(
                Arrays.asList(
                        new SolrQuery.SortClause("imgCrawlTimestamp", SolrQuery.ORDER.desc),
                        new SolrQuery.SortClause("imgWidth", SolrQuery.ORDER.asc)),
                solrQuery.getSorts());
    }

    // ---------------------------------------------------------------
    // Response-mapping tests: assert what the API returns given a
    // mocked Solr response
    // ---------------------------------------------------------------

    private SolrDocument sampleDocument(Date timestamp) {
        SolrDocument doc = new SolrDocument();
        doc.addField("id", "abc123");
        doc.addField("imgUrl", "http://example.com/img.jpg");
        doc.addField("imgMimeType", "image/jpeg");
        doc.addField("imgHeight", 100);
        doc.addField("imgWidth", 200);
        doc.addField("imgCrawlTimestamp", timestamp);
        doc.addField("imgTitle", "A title");
        doc.addField("imgAlt", "Alt text");
        doc.addField("imgCaption", "A caption");
        doc.addField("pageUrl", "http://example.com/page.html");
        doc.addField("pageCrawlTimestamp", timestamp);
        doc.addField("pageTitle", "Page title");
        doc.addField("collection", "myCollection");
        doc.addField("safe", 0.1f);
        doc.addField("pageHost", "example.com");
        return doc;
    }

    @Test
    void responseMapsSolrFieldsToV1NamesAndBuildsArchiveLinks() throws Exception {
        SimpleDateFormat v1 = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        Date timestamp = v1.parse("20200115120000");

        SolrDocumentList docs = new SolrDocumentList();
        docs.add(sampleDocument(timestamp));
        docs.setNumFound(1);
        docs.setStart(0);
        lenient().when(queryResponse.getResults()).thenReturn(docs);

        JsonObject json = runAndParseJsonResponse();

        assertEquals(1, json.get("totalItems").getAsLong());
        assertEquals(1, json.get("numberOfResponseItems").getAsInt());

        JsonObject item = json.getAsJsonArray("responseItems").get(0).getAsJsonObject();
        assertEquals("abc123", item.get("imgDigest").getAsString());
        assertEquals("http://example.com/img.jpg", item.get("imgSrc").getAsString());
        assertEquals("image/jpeg", item.get("imgMimeType").getAsString());
        assertEquals("20200115120000", item.get("imgTstamp").getAsString());
        assertEquals("http://example.com/page.html", item.get("pageURL").getAsString());
        assertEquals("20200115120000", item.get("pageTstamp").getAsString());
        assertEquals("myCollection", item.get("collection").getAsString());

        String expectedWayback = ImageSearchProperties.get("waybackAddress");
        assertEquals(expectedWayback + "20200115120000im_/http://example.com/img.jpg",
                item.get("imgLinkToArchive").getAsString());
        assertEquals(expectedWayback + "20200115120000/http://example.com/page.html",
                item.get("pageLinkToArchive").getAsString());

        // "safe" is not part of the default field list, so it must not appear unless requested
        assertFalse(item.has("safe"));
    }

    @Test
    void moreParamAddsFieldsNotInDefaultFieldListToResponse() throws Exception {
        params.put("more", "pageHost,safe");

        SimpleDateFormat v1 = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        Date timestamp = v1.parse("20200115120000");

        SolrDocumentList docs = new SolrDocumentList();
        docs.add(sampleDocument(timestamp));
        docs.setNumFound(1);
        docs.setStart(0);
        lenient().when(queryResponse.getResults()).thenReturn(docs);

        JsonObject json = runAndParseJsonResponse();

        JsonObject item = json.getAsJsonArray("responseItems").get(0).getAsJsonObject();
        assertEquals("example.com", item.get("pageHost").getAsString());
        assertEquals(0.9f, item.get("safe").getAsFloat(), 0.0001f);
    }

    @Test
    void prettyPrintTrueProducesIndentedJson() throws Exception {
        params.put("prettyPrint", "true");

        runAndCaptureSolrQuery();

        assertTrue(responseBody.toString().contains("{\n"));
    }

    @Test
    void prettyPrintTrueAlsoAddsLinkToDocumentation() throws Exception {
        // ImageSearchServlet reuses the prettyPrint flag as the ImageSearchResults "documentation" flag,
        // so turning on pretty printing also (perhaps unintentionally) surfaces this link.
        params.put("prettyPrint", "true");

        JsonObject json = runAndParseJsonResponse();

        assertEquals("https://github.com/arquivo/pwa-technologies/wiki/ImageSearch-API-v1.1-(beta)",
                json.get("linkToDocumentation").getAsString());
    }

    @Test
    void prettyPrintAbsentOmitsLinkToDocumentation() throws Exception {
        JsonObject json = runAndParseJsonResponse();

        assertFalse(json.has("linkToDocumentation"));
    }

    @Test
    void fieldsParamRestrictsReturnedItemFieldsToRequestedOnes() throws Exception {
        params.put("fields", "imgSrc");

        SimpleDateFormat v1 = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        Date timestamp = v1.parse("20200115120000");

        SolrDocumentList docs = new SolrDocumentList();
        docs.add(sampleDocument(timestamp));
        docs.setNumFound(1);
        docs.setStart(0);
        lenient().when(queryResponse.getResults()).thenReturn(docs);

        JsonObject json = runAndParseJsonResponse();

        JsonObject item = json.getAsJsonArray("responseItems").get(0).getAsJsonObject();
        assertTrue(item.has("imgSrc"));
        assertFalse(item.has("imgMimeType"));
        assertFalse(item.has("imgDigest"));
        assertFalse(item.has("collection"));
    }

    @Test
    void emptyResultSetProducesEmptyResponseItems() throws Exception {
        SolrDocumentList docs = new SolrDocumentList();
        docs.setNumFound(0);
        docs.setStart(0);
        lenient().when(queryResponse.getResults()).thenReturn(docs);

        JsonObject json = runAndParseJsonResponse();

        assertEquals(0, json.get("totalItems").getAsLong());
        assertEquals(0, json.get("numberOfResponseItems").getAsInt());
        assertEquals(0, json.getAsJsonArray("responseItems").size());
    }

    @Test
    void pagingOffsetsAreComputedFromStartLimitAndNumFound() throws Exception {
        params.put("offset", "30");
        params.put("maxItems", "10");
        lenient().when(request.getQueryString()).thenReturn("offset=30&maxItems=10");

        SolrDocumentList docs = new SolrDocumentList();
        docs.setNumFound(50);
        docs.setStart(30);
        lenient().when(queryResponse.getResults()).thenReturn(docs);

        JsonObject json = runAndParseJsonResponse();

        assertTrue(json.get("nextPage").getAsString().endsWith("&offset=40"));
        assertTrue(json.get("previousPage").getAsString().endsWith("&offset=20"));
    }

    @Test
    void previousPageOffsetIsClampedToZeroAtStartOfResults() throws Exception {
        params.put("offset", "0");
        params.put("maxItems", "10");
        lenient().when(request.getQueryString()).thenReturn("offset=0&maxItems=10");

        SolrDocumentList docs = new SolrDocumentList();
        docs.setNumFound(50);
        docs.setStart(0);
        lenient().when(queryResponse.getResults()).thenReturn(docs);

        JsonObject json = runAndParseJsonResponse();

        assertTrue(json.get("previousPage").getAsString().endsWith("&offset=0"));
    }

    @Test
    void nextPageOffsetIsClampedToNumFoundOnLastPage() throws Exception {
        params.put("offset", "45");
        params.put("maxItems", "10");
        lenient().when(request.getQueryString()).thenReturn("offset=45&maxItems=10");

        SolrDocumentList docs = new SolrDocumentList();
        docs.setNumFound(50);
        docs.setStart(45);
        lenient().when(queryResponse.getResults()).thenReturn(docs);

        JsonObject json = runAndParseJsonResponse();

        assertTrue(json.get("nextPage").getAsString().endsWith("&offset=50"));
    }

    @Test
    void linkToMoreFieldsReplacesExistingMoreQueryParamWithCanonicalFieldList() throws Exception {
        params.put("q", "cats");
        params.put("more", "foo");
        lenient().when(request.getQueryString()).thenReturn("q=cats&more=foo");

        JsonObject json = runAndParseJsonResponse();

        String linkToMoreFields = json.get("linkToMoreFields").getAsString();
        assertEquals("http://localhost/imagesearch?q=cats&more=pageHost,matchingImages,safe", linkToMoreFields);
    }

    @Test
    void generatedUrlsIncludeNonDefaultHttpPort() throws Exception {
        lenient().when(request.getServerPort()).thenReturn(8983);

        JsonObject json = runAndParseJsonResponse();

        assertTrue(json.get("linkToMoreFields").getAsString().startsWith("http://localhost:8983/imagesearch"));
    }

    @Test
    void generatedUrlsOmitDefaultHttpsPort() throws Exception {
        lenient().when(request.getScheme()).thenReturn("https");
        lenient().when(request.getServerPort()).thenReturn(443);

        JsonObject json = runAndParseJsonResponse();

        String linkToMoreFields = json.get("linkToMoreFields").getAsString();
        assertTrue(linkToMoreFields.startsWith("https://localhost/imagesearch"));
        assertFalse(linkToMoreFields.contains(":443"));
    }

    @Test
    void debugModeWrapsResponseWithResponseHeader() throws Exception {
        params.put("debug", "on");

        JsonObject json = runAndParseJsonResponse();

        assertTrue(json.has("response"));
        assertTrue(json.has("responseHeader"));
    }

    @Test
    void solrFailureProducesHttp500AndErrorBody() throws Exception {
        lenient().when(solrClient.query(any(SolrQuery.class)))
                .thenThrow(new SolrServerException("boom"));

        JsonObject json = runAndParseJsonResponse();

        verify(response).setStatus(500);
        JsonObject error = json.getAsJsonObject("error");
        assertEquals("boom", error.get(SolrServerException.class.getCanonicalName()).getAsString());
    }
}
