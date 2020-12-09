package pt.arquivo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

public class ImageSearchResults {
    String serviceName = "Arquivo.pt - image search service.";
    String linkToService = "https://arquivo.pt/images.jsp";
    String linkToDocumentation;
    String linkToMoreFields = "";
    String nextPage = "";
    String previousPage = "";
    long totalItems;
    int numberOfResponseItems;
    long offset;
    public static final String SAFE = "safe";
    public static final String IMAGETSTAMP = "imgCrawlTimestamp";
    public static final String IMAGESRC = "imgUrl";
    public static final String IMAGELINKTOARCHIVE = "imgLinkToArchive";
    public static final String PAGELINKTOARCHIVE = "pageLinkToArchive";
    public static final String PAGEURL = "pageUrl";
    public static final String PAGETSTAMP = "pageCrawlTimestamp";
    public static final String IMGSRCBASE64 = "imgSrcBase64";
    public static final String IMGTHUMBNAILBASE64 = "imgThumbnailBase64";
    public static final String WAYBACKADDRESS = "https://arquivo.pt/wayback/";

    private static final SimpleDateFormat FORMAT_IN = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final SimpleDateFormat FORMAT_OUT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);

    SolrDocumentList responseItems;

    public ImageSearchResults(long totalItems, int numberOfResponseItems, long offset, String linkToMoreFields, String nextPage, String previousPage, SolrDocumentList responseItems, boolean documentation) {
        this.nextPage = nextPage;
        this.previousPage = previousPage;
        if (documentation) {
            linkToDocumentation = "https://github.com/arquivo/pwa-technologies/wiki/ImageSearch-API-v1-(beta)";
        }
        this.linkToMoreFields = linkToMoreFields;
        this.totalItems = totalItems;
        this.numberOfResponseItems = numberOfResponseItems;
        if (totalItems < numberOfResponseItems) { /*E.g. if total_items=0 than we are showing 0 images max*/
            this.numberOfResponseItems = (int) totalItems;
        }
        this.offset = offset;
        this.responseItems = parseDocuments(responseItems);

        TimeZone zone = TimeZone.getTimeZone("GMT");
        FORMAT_IN.setTimeZone(zone);
        FORMAT_OUT.setTimeZone(zone);
    }

    private SolrDocumentList parseDocuments(SolrDocumentList response_items) {
        SolrDocumentList processedDocs = new SolrDocumentList();
        SolrDocument current = null;

        for (int i = 0; i < response_items.size(); i++) {
            SolrDocument newDocument = new SolrDocument();
            current = response_items.get(i);
            Set<String> keyNames = current.keySet();
            for (String key : keyNames) {
                switch (key) {
                    case SAFE:
                        newDocument.addField(SAFE, 1.0f - (float) current.getFieldValue(SAFE));
                        break;
                    case PAGETSTAMP:
                        newDocument.addField(PAGETSTAMP, FORMAT_IN.format(current.getFieldValue(PAGETSTAMP)));
                        break;
                    case IMAGETSTAMP:
                        newDocument.addField(IMAGETSTAMP, FORMAT_IN.format(current.getFieldValue(IMAGETSTAMP)));
                        break;
                    case IMGSRCBASE64:
                        newDocument.addField(IMGTHUMBNAILBASE64, current.getFieldValue(IMGSRCBASE64));
                        break;
                    default:
                        newDocument.addField(key, current.getFieldValue(key));
                        break;
                }
            }
            if (newDocument.containsKey(IMAGESRC) && newDocument.containsKey(IMAGETSTAMP)) {
                newDocument.addField(IMAGELINKTOARCHIVE, WAYBACKADDRESS + newDocument.getFieldValue(IMAGETSTAMP) + "im_/" + newDocument.getFieldValue(IMAGESRC));
            }
            if (newDocument.containsKey(PAGEURL) && newDocument.containsKey(PAGETSTAMP)) {
                newDocument.addField(PAGELINKTOARCHIVE, WAYBACKADDRESS + newDocument.getFieldValue(PAGETSTAMP) + "/" + newDocument.getFieldValue(PAGEURL));
            }

            processedDocs.add(newDocument);
        }
        return processedDocs;
    }
}
