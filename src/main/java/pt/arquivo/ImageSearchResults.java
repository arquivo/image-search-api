package pt.arquivo;

import java.text.SimpleDateFormat;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Set;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;


public class ImageSearchResults {
    private SimpleDateFormat V1_DATE_FORMAT;

    String serviceName = "Arquivo.pt - image search service.";
    String linkToService = ImageSearchProperties.get("linkToService");
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
    public static final String V2_WAYBACKADDRESS = ImageSearchProperties.get("waybackAddress");
    
    // Hashtable to quickly check if each field should be included in the returned document (to support "fields" query)
    private Dictionary<String,Boolean> fieldReturnability = new Hashtable<String,Boolean>();


    SolrDocumentList responseItems;

    public ImageSearchResults(String[] requestedFields, long totalItems, int numberOfResponseItems, long offset, String linkToMoreFields, String nextPage, String previousPage, SolrDocumentList responseItems, boolean documentation) {
        this.nextPage = nextPage;
        this.previousPage = previousPage;

        if (documentation) {
            linkToDocumentation = "https://github.com/arquivo/pwa-technologies/wiki/ImageSearch-API-v1.1-(beta)";
        }

        this.linkToMoreFields = linkToMoreFields;
        this.totalItems = totalItems;
        this.numberOfResponseItems = numberOfResponseItems;
        if (totalItems < numberOfResponseItems) { /*E.g. if total_items=0 than we are showing 0 images max*/
            this.numberOfResponseItems = (int) totalItems;
        }
        this.offset = offset;

        this.V1_DATE_FORMAT = (SimpleDateFormat)APIVersionTranslator.V1_DATE_FORMAT.clone();

        this.responseItems = parseDocuments(requestedFields, responseItems);

    }

    private SolrDocumentList parseDocuments(String[] requestedFields, SolrDocumentList response_items) {
        SolrDocumentList processedDocs = new SolrDocumentList();
        SolrDocument current = null;

        for (SolrDocument response_item : response_items) {
            SolrDocument newDocument = new SolrDocument();
            current = response_item;
            Set<String> keyNames = current.keySet();
            for (String key : keyNames) {
                if(isRequestedField(requestedFields, APIVersionTranslator.v2Tov1(key))){
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
            }

            String V1_IMAGELINKTOARCHIVE = APIVersionTranslator.v2Tov1(V2_IMAGELINKTOARCHIVE);

            if (isRequestedField(requestedFields,V1_IMAGELINKTOARCHIVE)) {
                String tstamp = this.V1_DATE_FORMAT.format(current.getFieldValue(V2_IMAGETSTAMP));
                String url = current.getFieldValue(V2_IMAGEURL).toString();
                newDocument.addField(V1_IMAGELINKTOARCHIVE, V2_WAYBACKADDRESS + tstamp + "im_/" + url);
            }

            String V1_PAGELINKTOARCHIVE = APIVersionTranslator.v2Tov1(V2_PAGELINKTOARCHIVE);

            if (isRequestedField(requestedFields, V1_PAGELINKTOARCHIVE)) {
                String tstamp = this.V1_DATE_FORMAT.format(current.getFieldValue(V2_PAGETSTAMP));
                String url = current.getFieldValue(V2_PAGEURL).toString();
                newDocument.addField(V1_PAGELINKTOARCHIVE, V2_WAYBACKADDRESS + tstamp + "/" + url);
            }

            processedDocs.add(newDocument);
        }
        return processedDocs;
    }

    private Boolean isRequestedField(String[] requestedFields, String field){
        if (this.fieldReturnability.get(field)==null){
            Boolean r = false;
            for (String f:requestedFields){
                if (f.equals(field)){
                    r = true;
                    break;
                }
            }
            fieldReturnability.put(field, r);
            return r;
        }
        return this.fieldReturnability.get(field);
    }
}
