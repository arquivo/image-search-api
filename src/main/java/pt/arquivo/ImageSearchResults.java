package pt.arquivo;

import java.text.SimpleDateFormat;
import java.util.Set;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;


public class ImageSearchResults {
    private SimpleDateFormat V1_DATE_FORMAT;

    String serviceName = "Arquivo.pt - image search service.";
    String linkToService = "https://arquivo.pt/images.jsp";
    String linkToDocumentation;
    String linkToMoreFields = "";
    String nextPage = "";
    String previousPage = "";
    long totalItems;
    int numberOfResponseItems;
    long offset;
    public static final String V2_SAFE = "safe";
    public static final String V2_IMAGETSTAMP = "imgCrawlTimestamp";
    public static final String V2_IMAGEURL = "imgUrl";
    public static final String V2_IMAGELINKTOARCHIVE = "imgLinkToArchive";
    public static final String V2_PAGELINKTOARCHIVE = "pageLinkToArchive";
    public static final String V2_PAGEURL = "pageUrl";
    public static final String V2_PAGETSTAMP = "pageCrawlTimestamp";
    public static final String V2_WAYBACKADDRESS = "https://arquivo.pt/wayback/";



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

        this.V1_DATE_FORMAT = (SimpleDateFormat)APIVersionTranslator.V1_DATE_FORMAT.clone();

        this.responseItems = parseDocuments(responseItems);

    }

    private SolrDocumentList parseDocuments(SolrDocumentList response_items) {
        SolrDocumentList processedDocs = new SolrDocumentList();
        SolrDocument current = null;

        for (SolrDocument response_item : response_items) {
            SolrDocument newDocument = new SolrDocument();
            current = response_item;
            Set<String> keyNames = current.keySet();
            for (String key : keyNames) {
                switch (key) {
                    case V2_SAFE:
                        newDocument.addField(APIVersionTranslator.v2Tov1(V2_SAFE), 1.0f - (float) current.getFieldValue(V2_SAFE));
                        break;
                    case V2_PAGETSTAMP:
                        newDocument.addField(APIVersionTranslator.v2Tov1(V2_PAGETSTAMP), this.V1_DATE_FORMAT.format(current.getFieldValue(V2_PAGETSTAMP)));
                        break;
                    case V2_IMAGETSTAMP:
                        newDocument.addField(APIVersionTranslator.v2Tov1(V2_IMAGETSTAMP), this.V1_DATE_FORMAT.format(current.getFieldValue(V2_IMAGETSTAMP)));
                        break;
                    default:
                        newDocument.addField(APIVersionTranslator.v2Tov1(key), current.getFieldValue(key));
                        break;
                }
            }
            String V1_IMAGEURL = APIVersionTranslator.v2Tov1(V2_IMAGEURL);
            String V1_IMAGETSTAMP = APIVersionTranslator.v2Tov1(V2_IMAGETSTAMP);
            String V1_IMAGELINKTOARCHIVE = APIVersionTranslator.v2Tov1(V2_IMAGELINKTOARCHIVE);

            if (newDocument.containsKey(V1_IMAGEURL) && newDocument.containsKey(V1_IMAGETSTAMP)) {
                newDocument.addField(V1_IMAGELINKTOARCHIVE, V2_WAYBACKADDRESS + newDocument.getFieldValue(V1_IMAGETSTAMP) + "im_/" + newDocument.getFieldValue(V1_IMAGEURL));
            }

            String V1_PAGEURL = APIVersionTranslator.v2Tov1(V2_PAGEURL);
            String V1_PAGETSTAMP = APIVersionTranslator.v2Tov1(V2_PAGETSTAMP);
            String V1_PAGELINKTOARCHIVE = APIVersionTranslator.v2Tov1(V2_PAGELINKTOARCHIVE);

            if (newDocument.containsKey(V1_PAGEURL) && newDocument.containsKey(V1_PAGETSTAMP)) {
                newDocument.addField(V1_PAGELINKTOARCHIVE, V2_WAYBACKADDRESS + newDocument.getFieldValue(V1_PAGETSTAMP) + "/" + newDocument.getFieldValue(V1_PAGEURL));
            }

            processedDocs.add(newDocument);
        }
        return processedDocs;
    }
}
