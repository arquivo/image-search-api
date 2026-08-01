package pt.arquivo;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class APIVersionTranslatorTest {

    @Test
    void v1Tov2TranslatesKnownFieldNames() {
        assertEquals("imgUrl", APIVersionTranslator.v1Tov2("imgSrc"));
        assertEquals("pageUrl", APIVersionTranslator.v1Tov2("pageURL"));
        assertEquals("imgCrawlTimestamp", APIVersionTranslator.v1Tov2("imgTstamp"));
        assertEquals("pageCrawlTimestamp", APIVersionTranslator.v1Tov2("pageTstamp"));
        assertEquals("imagesInOriginalPage", APIVersionTranslator.v1Tov2("pageImages"));
        assertEquals("imgSrcBase64", APIVersionTranslator.v1Tov2("imgThumbnailBase64"));
        assertEquals("id", APIVersionTranslator.v1Tov2("imgDigest"));
    }

    @Test
    void v1Tov2PassesThroughUnknownFieldNames() {
        assertEquals("imgMimeType", APIVersionTranslator.v1Tov2("imgMimeType"));
        assertEquals("collection", APIVersionTranslator.v1Tov2("collection"));
    }

    @Test
    void v2Tov1TranslatesKnownFieldNames() {
        assertEquals("imgSrc", APIVersionTranslator.v2Tov1("imgUrl"));
        assertEquals("pageURL", APIVersionTranslator.v2Tov1("pageUrl"));
        assertEquals("imgTstamp", APIVersionTranslator.v2Tov1("imgCrawlTimestamp"));
        assertEquals("pageTstamp", APIVersionTranslator.v2Tov1("pageCrawlTimestamp"));
        assertEquals("pageImages", APIVersionTranslator.v2Tov1("imagesInOriginalPage"));
        assertEquals("imgThumbnailBase64", APIVersionTranslator.v2Tov1("imgSrcBase64"));
        assertEquals("imgDigest", APIVersionTranslator.v2Tov1("id"));
    }

    @Test
    void v2Tov1PassesThroughUnknownFieldNames() {
        assertEquals("imgMimeType", APIVersionTranslator.v2Tov1("imgMimeType"));
        assertEquals("pageHost", APIVersionTranslator.v2Tov1("pageHost"));
    }

    @Test
    void v1DateFormatParsesAndFormatsInGMT() throws ParseException {
        assertEquals(TimeZone.getTimeZone("GMT"), APIVersionTranslator.V1_DATE_FORMAT.getTimeZone());

        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        cal.clear();
        cal.set(2020, Calendar.JANUARY, 15, 12, 0, 0);

        assertEquals("20200115120000", APIVersionTranslator.V1_DATE_FORMAT.format(cal.getTime()));
        assertEquals(cal.getTime(), APIVersionTranslator.V1_DATE_FORMAT.parse("20200115120000"));
    }

    @Test
    void v2DateFormatFormatsInGMTWithMillisAndOffset() {
        assertEquals(TimeZone.getTimeZone("GMT"), APIVersionTranslator.V2_DATE_FORMAT.getTimeZone());

        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        cal.clear();
        cal.set(2020, Calendar.JANUARY, 15, 12, 0, 0);

        assertEquals("2020-01-15T12:00:00.000Z", APIVersionTranslator.V2_DATE_FORMAT.format(cal.getTime()));
    }
}
