package pt.arquivo;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient.Builder;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * ImageSearch API Back-End.
 * Servlet responsible for returning a json object with the results of the query received in the parameter.
 *
 * @author fmelo
 * @version 1.0
 */
public class ImageSearchServlet extends HttpServlet {
    /**
     * Class responsible for:
     * Search the indexes Lucene, through the calls to the queryServers.
     * Search by URL in CDX indexes, through the CDXServer API.
     * <p>
     * Documentation: https://arquivo.pt/api -
     */


    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ImageSearchServlet.class);
    private static String collectionsHost = null;
    private static String solrHost = null;
    private static String solrCollection = null;
    private static final SimpleDateFormat FORMAT_IN = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final SimpleDateFormat FORMAT_OUT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);
    Calendar DATE_END = new GregorianCalendar();
    private static final String DEFAULT_FL_STRING = "imgDigest,imgSrc,imgMimeType,imgHeight,imgWidth,imgTstamp,imgTitle,imgAlt,imgCaption,pageURL,pageTstamp,pageTitle,collection";
    private static final String MOREFIELDS = "imgThumbnailBase64,imgSrcURLDigest,imgDigest,pageProtocol,pageHost,pageImages,safe";
    private static final Map<String, Integer> DEFAULT_QUERY_FIELDS = new HashMap<String, Integer>() {{
        put("imgTitle", 4);
        put("imgAlt", 3);
        put("imgCaption", 3);
        put("imgSrcTokens", 2);
        put("pageTitle", 1);
        put("pageURLTokens", 1);
    }};

    private ArrayList<String> fqStrings;
    private String q;

    /**
     * HttpServlet init method.
     *
     * @param config: nutchwax configuration
     * @return void
     */
    public void init(ServletConfig config) throws ServletException {

        collectionsHost = config.getInitParameter("waybackHost");
        solrHost = config.getInitParameter("solrServer");
        solrCollection = config.getInitParameter("solrCollection");

        TimeZone zone = TimeZone.getTimeZone("GMT");
        FORMAT_IN.setTimeZone(zone);
        FORMAT_OUT.setTimeZone(zone);


        if (collectionsHost == null) {
            LOG.debug("[init] Null waybackHost parameter in Web.xml");
        }
        if (solrHost == null) {
            LOG.debug("[init] Null solrHost parameter in Web.xml");
        }
        if (solrCollection == null) {
            LOG.debug("[init] Null waybackHost parameter in Web.xml");
            throw new ServletException("ERROR solrCollection in Web.xml");
        }

    }


    /**
     * HttpServlet doGet method
     *
     * @param request  - type HttpServletRequest
     * @param response - type HttpServletResponse
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        fqStrings = new ArrayList<String>();
        LOG.debug("[doGet] query request from " + request.getRemoteAddr());
        String requestURL = request.getScheme() + "://" +
                request.getServerName() +
                ("http".equals(request.getScheme()) && request.getServerPort() == 80 || "https".equals(request.getScheme()) && request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) +
                request.getRequestURI() +
                (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        LOG.debug("[imagesearch request] : " + requestURL);

        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD");

        int start = 0;
        int limit = 50; /*Default number of results*/

        long startTime;
        long endTime;
        long duration;

        Object imgSearchResponse = null;
        ImageSearchResults imgSearchResults = null;
        String safeSearch = "";
        String flString = ""; /*limit response fields*/
        String jsonSolrResponse = "";

        // get parameters from request
        request.setCharacterEncoding("UTF-8");

        q = request.getParameter("q");

        if (q == null) {
            q = "";
        }

        // first hit to display
        String startString = request.getParameter("offset");
        if (startString != null)
            start = parseToIntWithDefault(startString, 0);

        // number of items to display
        String limitString = request.getParameter("maxItems");
        if (limitString != null)
            limit = parseToIntWithDefault(limitString, 50);

        if (limit < 0)
            limit = 0;

        if (limit > 200)
            limit = 200; //Max Number of Results 200 in one request?

        // date restriction
        String dateStart = request.getParameter("from");
        if (dateStart == null || dateStart.length() == 0) {
            dateStart = "1996-01-01T00:00:00Z";
            //dateStart = FORMAT_OUT.format( dateStart );
        }
        String dateEnd = request.getParameter("to");
        if (dateEnd == null || dateEnd.length() == 0) {
            Calendar dateEND = currentDate();
            dateEnd = FORMAT_OUT.format(dateEND.getTime());
        }

        if (dateStart != null && dateEnd != null) { //Logic to accept pages with yyyy and yyyyMMddHHmmss format

            try {
                FORMAT_OUT.setLenient(false);
                DateFormat dOutputFormatYear = new SimpleDateFormat("yyyy");
                dOutputFormatYear.setLenient(false);
                if (tryParse(FORMAT_IN, dateStart)) {
                    Date dStart = FORMAT_IN.parse(dateStart);
                    dateStart = FORMAT_OUT.format(dStart.getTime());
                } else if (tryParse(FORMAT_IN, dateStart + "0101000000")) {
                    Date dStart = FORMAT_IN.parse(dateStart + "0101000000");
                    dateStart = FORMAT_OUT.format(dStart.getTime());
                } else {
                    dateStart = "1996-01-01T00:00:00Z";
                }

                if (tryParse(FORMAT_IN, dateEnd)) {
                    Date dEnd = FORMAT_IN.parse(dateEnd);
                    dateEnd = FORMAT_OUT.format(dEnd.getTime());
                } else if (tryParse(FORMAT_IN, dateEnd + "1231235959")) {
                    Date dEnd = FORMAT_IN.parse(dateEnd + "1231235959");
                    dateEnd = FORMAT_OUT.format(dEnd.getTime());
                } else {
                    Calendar dateEND = currentDate();
                    dateEnd = FORMAT_OUT.format(dateEND.getTime());
                }
            } catch (ParseException e) {
                LOG.error("Parse Exception: ", e);
            } catch (IndexOutOfBoundsException e) {
                LOG.error("Parse Exception: ", e);
            }
        }
        fqStrings.add("imgTstamp:[" + dateStart + " TO " + dateEnd + "]");
        safeSearch = request.getParameter("safeSearch");
        if (!"off".equals(safeSearch)) {
            fqStrings.add("safe:[0 TO 0.49]"); /*Default behaviour is to limit safe score from 0 -> 0.49; else show all images*/
        }

        String typeParameter = request.getParameter("type");
        if (typeParameter == null)
            typeParameter = "";
        if (!typeParameter.equals("")) {
            if (typeParameter.toLowerCase().equals("jpeg") || typeParameter.toLowerCase().equals("jpg")) {
                fqStrings.add("imgMimeType:image/jpeg OR imgMimeType:image/jpg");
            } else {
                fqStrings.add("imgMimeType: image/" + typeParameter);
            }
        }
        String sizeParameter = request.getParameter("size");
        if (sizeParameter == null)
            sizeParameter = "";
        if (!sizeParameter.equals("")) {
            if (sizeParameter.equals("sm")) {
                fqStrings.add("{!frange u=65536 }product(imgHeight,imgWidth)"); /*images up to 65536pixels² of area - i.e. max square size of 256x256px*/
            } else if (sizeParameter.equals("md")) {
                fqStrings.add("{!frange l=65537 u=810000 }product(imgHeight,imgWidth)"); /*images between 65537pixels² of area , up to  810000px² of area - i.e. max square size of 900x900px*/
            } else if (sizeParameter.equals("lg")) {
                fqStrings.add("{!frange l=810001}product(imgHeight,imgWidth)"); /*images bigger than 810000px² of area*/
            }
        }
        if (request.getParameter("more") != null) {
            flString += request.getParameter("more").replaceAll("imgThumbnailBase64", "imgSrcBase64") + ",";
        }
        if (request.getParameter("fields") != null) {
            flString += request.getParameter("fields");
        } else { //default params
            flString += DEFAULT_FL_STRING;
        }
        if (request.getParameter("siteSearch") != null) {
            fqStrings.add("pageURL:*" + ClientUtils.escapeQueryChars(request.getParameter("siteSearch")) + "*");
        }
        String getDuplicates = request.getParameter("duplicates");
        if (!"on".equals(getDuplicates)) {
            fqStrings.add("{!collapse field=imgDigest}");
        }

        String requestedCollection = request.getParameter("collection");
        if (requestedCollection != null && requestedCollection.length() > 0) {
            fqStrings.add(Arrays.asList(requestedCollection.split(",")).stream().map(c -> "collection:" + c).collect(Collectors.joining(" OR ")));
        }

        /*Process operators such as site: type: and site: inside the q parameter*/
        /*Should we allow people to use those operators when calling the api e.g.
         * /imagesearch?q=sapo%20site:sapo.pt%20type:jpeg instead of
         * /imagesearch?q=sapo&siteSearch=sapo.pt&type=jpeg */
        q = checkSpecialOperators();

        //Pretty print in output message
        String prettyPrintParameter = request.getParameter("prettyPrint");
        boolean prettyOutput = false;
        if (prettyPrintParameter != null && prettyPrintParameter.equals("true"))
            prettyOutput = true;

        startTime = System.currentTimeMillis();
        //execute the query
        try {
            LOG.debug("Wayback HOST: " + collectionsHost);
            LOG.debug("SOLR HOST: " + solrHost);
            SolrClient solr;


            if (solrHost.contains(",")) {
                Builder builder = new CloudSolrClient.Builder();
                builder.withZkHost(
                        Arrays.asList(new String[]{solrHost}));
                solr = (CloudSolrClient) builder.build();
                ((CloudSolrClient) solr).setDefaultCollection(solrCollection);
            } else {
                solr = new HttpSolrClient.Builder(solrHost + solrCollection).build();
            }


            SolrQuery solrQuery = new SolrQuery();

            if (q.trim().isEmpty()) {
                q = "*:*";
            }


            solrQuery.setQuery(q);
            LOG.debug("FilterQuery Strings:" + fqStrings);

            for (String fq : fqStrings) {
                solrQuery.addFilterQuery(fq);
            }

            solrQuery.set("defType", "edismax");

            StringBuilder qs = new StringBuilder();
            for (Map.Entry<String, Integer> entry : DEFAULT_QUERY_FIELDS.entrySet())
                qs.append(String.format("%s^%d ", entry.getKey(), entry.getValue()));
            solrQuery.set("qf", qs.toString());


            qs = new StringBuilder();
            for (Map.Entry<String, Integer> entry : DEFAULT_QUERY_FIELDS.entrySet())
                qs.append(String.format("%s^%d ", entry.getKey(), entry.getValue()*1000));

            solrQuery.set("pf", qs.toString());
            solrQuery.set("ps", 1);

            qs = new StringBuilder();
            for (Map.Entry<String, Integer> entry : DEFAULT_QUERY_FIELDS.entrySet())
                qs.append(String.format("%s^%d ", entry.getKey(), entry.getValue()*100));

            solrQuery.set("pf2", qs.toString());
            solrQuery.set("ps2", 2);

            qs = new StringBuilder();
            for (Map.Entry<String, Integer> entry : DEFAULT_QUERY_FIELDS.entrySet())
                qs.append(String.format("%s^%d ", entry.getKey(), entry.getValue()*10));

            solrQuery.set("pf3", qs.toString());
            solrQuery.set("ps3", 3);


            solrQuery.setRows(limit);
            solrQuery.setStart(start);
            solrQuery.set("fl", flString);

            solrQuery.addSort("score", SolrQuery.ORDER.desc);
            solrQuery.addSort("field(imgTstamp,min)", SolrQuery.ORDER.asc);
            solrQuery.addSort("imgSrc", SolrQuery.ORDER.asc);

            LOG.debug("SOLR Query: " + solrQuery);

            QueryResponse responseSolr = null;
            try {
                responseSolr = solr.query(solrQuery);
            } catch (SolrServerException e) {
                LOG.debug("Solr Server Exception : " + e);
            }
			int invalidDocs = 0;
			SolrDocumentList documents = new SolrDocumentList();
			for(SolrDocument doc : responseSolr.getResults()){ /*Iterate Results*/
				if(flString.equals("") || flString.contains("imgSrcBase64")){
					byte[] bytesImgSrc64 = (byte[]) doc.getFieldValue("imgSrcBase64");
					if(bytesImgSrc64 == null){
						LOG.debug("Null image");
                        SolrQuery solrImgQuery = new SolrQuery();
                        String imgDigest = (String) doc.getFieldValue("imgDigest");
                        solrImgQuery.setQuery("type:image && imgDigest:" + imgDigest);
                        solrImgQuery.set("fl", "imgSrcBase64");
                        solrImgQuery.setRows(1);
                        QueryResponse responseImgSolr = null;
                        try {
                            responseImgSolr = solr.query(solrImgQuery);
                        } catch (SolrServerException e) {
                            LOG.debug("Solr Server Exception : " + e);
                        }
                        String imgSrc64 = "";
                        for(SolrDocument docImg : responseImgSolr.getResults()) {
                            bytesImgSrc64 = (byte[]) docImg.getFieldValue("imgSrcBase64");
                            byte[] encodedImgSrc64 = Base64.getEncoder().encode(bytesImgSrc64);
                            imgSrc64 = new String(encodedImgSrc64);
                        }
                        doc.setField("imgSrcBase64", imgSrc64);
					} else {
						byte[] encodedImgSrc64 = Base64.getEncoder().encode(bytesImgSrc64);
						String imgSrc64 = new String(encodedImgSrc64);
						doc.setField("imgSrcBase64", imgSrc64);
					}
					documents.add(doc);
				}
				else{
					documents.add(doc);
				}
			}
			  int numFound = (int) responseSolr.getResults().getNumFound();
			  int offsetPreviousPage;
			  if( start == 0 )
				  offsetPreviousPage = 0;
			  else {
				  offsetPreviousPage = start - limit;
				  offsetPreviousPage = (offsetPreviousPage < 0 ? 0 : offsetPreviousPage);
			  }
			  String previousPage = requestURL.replaceAll("&offset=([^&]+)", "").concat("&offset="+offsetPreviousPage);
			  int offsetNextPage = start + limit;
			  if(offsetNextPage > numFound){
				  offsetNextPage = numFound;
			  }
			  String nextPage = requestURL.replaceAll("&offset=([^&]+)", "").concat("&offset="+offsetNextPage);
			  String linkToMoreFields = requestURL.replaceAll("&more=([^&]+)", "").concat("&more="+MOREFIELDS);

            imgSearchResults = new ImageSearchResults(numFound, documents.size(), responseSolr.getResults().getStart(), linkToMoreFields, nextPage, previousPage, documents, prettyOutput);
            if (request.getParameter("debug") != null && request.getParameter("debug").equals("on")) {
                imgSearchResponse = new ImageSearchResponseDebug(responseSolr.getResponseHeader(), imgSearchResults);
            } else {
                imgSearchResponse = imgSearchResults;
            }
        } catch (IOException e) {
            LOG.warn("Search Error", e);
        }

        endTime = System.currentTimeMillis();

        try {
            if (prettyOutput) {
                Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
                jsonSolrResponse = gson.toJson(imgSearchResponse);
            } else {
                Gson gson = new GsonBuilder().disableHtmlEscaping().create();
                jsonSolrResponse = gson.toJson(imgSearchResponse);
            }

        } catch (JsonParseException e) {
            throw new ServletException(e);
        }
        //TODO:: callback option and setting jsonp content type in that case
        if (request.getParameter("callback") != null && !request.getParameter("callback").equals("")) {
            jsonSolrResponse = request.getParameter("callback") + "(" + jsonSolrResponse + ");";
            response.setContentType("text/javascript"); //jsonp
        } else {
            response.setContentType("application/json"); //json
        }
        response.setCharacterEncoding("UTF-8");
        duration = (endTime - startTime);
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }
        LOG.info("[ImageSearch API]" + "\t" + duration + "ms\t" + ipAddress + "\t" + requestURL);

        // Get the printwriter object from response to write the required json object to the output stream
        PrintWriter out = response.getWriter();
        out.print(jsonSolrResponse);
        out.flush();
    }


    /*************************************************************/
    /********************* AUXILIARY METHODS *********************/
    /************************************************************/


    private String checkSpecialOperators() {
        LOG.debug("checking special operators");
        if (q.contains("site:") || q.contains("type:") || q.contains("safe:") || q.contains("size:") || q.contains("duplicates:")) { /*query has a special operator we need to deal with it*/
            LOG.debug("found special operator");
            String[] words = q.split(" ");
            ArrayList<String> cleanWords = new ArrayList<String>();
            for (String word : words) {
                if (word.toLowerCase().startsWith("site:")) {
                    LOG.debug("found site:");
                    String domain = ClientUtils.escapeQueryChars(word.replace("site:", ""));
                    fqStrings.add("pageHost:*." + domain + " OR pageHost:" + domain);
                } else if (word.toLowerCase().startsWith("type:")) {
                    LOG.debug("found type:");
                    String typeWord = word.replace("type:", "");
                    if (!typeWord.equals("")) {
                        if (typeWord.toLowerCase().equals("jpeg") || typeWord.toLowerCase().equals("jpg")) {
                            fqStrings.add("imgMimeType:image/jpeg OR imgMimeType:image/jpg");
                        } else {
                            fqStrings.add("imgMimeType: image/" + typeWord);
                        }
                    }
                } else if (word.toLowerCase().startsWith("duplicates:")) {
                    LOG.debug("found duplicates:");
                    String safeWord = word.replace("duplicates:", "");
                    if (safeWord.toLowerCase().equals("off") || safeWord.toLowerCase().equals("on")){
                        fqStrings.remove("{!collapse field=imgDigest}");
                    }
                    if (!safeWord.toLowerCase().equals("on")) {
                        fqStrings.add("{!collapse field=imgDigest}");
                    }
                } else if (word.toLowerCase().startsWith("safe:")) {
                    LOG.debug("found safe:");
                    String safeWord = word.replace("safe:", "");
                    if (safeWord.toLowerCase().equals("off") || safeWord.toLowerCase().equals("on")) {
                        removeMatchingFqString("safe");
                    }
                    if (!safeWord.toLowerCase().equals("off")) {
                        fqStrings.add("safe:[0 TO 0.49]"); /*Default behaviour is to limit safe score from 0 -> 0.49; else show all images*/
                    }
                } else if (word.toLowerCase().startsWith("size:")) {
                    LOG.debug("found size:");
                    String sizeWord = word.replace("size:", "").toLowerCase();
                    LOG.debug("size word: " + sizeWord);
                    if (!sizeWord.equals("")) {
                        if (sizeWord.equals("sm")) {
                            LOG.debug("sm");
                            fqStrings.add("{!frange u=65536 }product(imgHeight,imgWidth)"); /*images up to 65536pixels² of area - i.e. max square size of 256x256px*/
                        } else if (sizeWord.equals("md")) {
                            LOG.debug("md");
                            fqStrings.add("{!frange l=65537 u=810000 }product(imgHeight,imgWidth)"); /*images between 65537pixels² of area , up to  810000px² of area - i.e. max square size of 900x900px*/
                        } else if (sizeWord.equals("lg")) {
                            LOG.debug("lg");
                            fqStrings.add("{!frange l=810001}product(imgHeight,imgWidth)"); /*images bigger than 810000px² of area*/
                        }
                    }

                } else {
                    LOG.debug(" found clean word");
                    cleanWords.add(word);
                }
            }
            return String.join(" ", cleanWords);
        } else return q;
    }


    private void removeMatchingFqString(String field) {
        for (int i = 0; i < fqStrings.size(); i++) {
            if (fqStrings.get(i).startsWith(field)) {
                fqStrings.remove(i);
            }
        }
    }

    /**
     * Converting a string to an integer, if it is not possible, returns a defaultVal value
     *
     * @param number
     * @param defaultVal
     * @return
     */
    public static int parseToIntWithDefault(String number, int defaultVal) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * Checks whether it is possible to parse from a string to a format
     *
     * @param df
     * @param s
     * @return
     */
    private static Boolean tryParse(DateFormat df, String s) {
        Boolean valid = false;
        try {
            df.parse(s);
            valid = true;
        } catch (ParseException e) {
            valid = false;
        }
        return valid;
    }


    /**
     * Returns the current date in the format (YYYYMMDDHHMMSS)
     *
     * @return
     */
    private static Calendar currentDate() {
        Calendar DATE_END = new GregorianCalendar();
        DATE_END.set(Calendar.YEAR, DATE_END.get(Calendar.YEAR));
        DATE_END.set(Calendar.MONTH, 12 - 1);
        DATE_END.set(Calendar.DAY_OF_MONTH, 31);
        DATE_END.set(Calendar.HOUR_OF_DAY, 23);
        DATE_END.set(Calendar.MINUTE, 59);
        DATE_END.set(Calendar.SECOND, 59);
        return DATE_END;
    }

}