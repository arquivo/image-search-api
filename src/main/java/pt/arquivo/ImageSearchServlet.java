package pt.arquivo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.arquivo.responses.ImageSearchErrorResponse;
import pt.arquivo.responses.ImageSearchResponseDebug;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static pt.arquivo.APIVersionTranslator.V1_DATE_FORMAT;
import static pt.arquivo.ImageSearchResults.V2_IMAGEURL;
import static pt.arquivo.ImageSearchResults.V2_IMAGETSTAMP;
import static pt.arquivo.ImageSearchResults.V2_PAGEURL;
import static pt.arquivo.ImageSearchResults.V2_PAGETSTAMP;

/**
 * ImageSearch API Back-End.
 * Servlet responsible for returning a json object with the results of the query received in the parameter.
 *
 * @author fmelo
 * @version 1.0
 */
public class ImageSearchServlet extends HttpServlet {
    // SolrClient is thread safe, meaning that it can be shared among all requests
    private SolrClient solr;
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ImageSearchServlet.class);
    private static String collectionsHost = null;
    private static String solrHost = null;
    private static String solrCollection = null;
    Calendar DATE_END = new GregorianCalendar();
    private static final String V1_DEFAULT_FL_STRING = "imgDigest,imgSrc,imgMimeType,imgHeight,imgWidth,imgTstamp,imgTitle,imgAlt,imgCaption,pageURL,pageTstamp,pageTitle,collection,imgLinkToArchive,pageLinkToArchive";
    private static final String V1_MOREFIELDS = "pageHost,matchingImages,safe";

    private static final Map<String, Integer> DEFAULT_QUERY_FIELDS = new HashMap<String, Integer>() {{
        put("imgTitle", 4);
        put("imgAlt", 3);
        put("imgCaption", 3);
        put("imgUrlTokens", 2);
        put("pageTitle", 1);
        put("pageUrlTokens", 1);
    }};

    /**
     * HttpServlet init method.
     *
     * @param config: nutchwax configuration
     */
    public void init(ServletConfig config) throws ServletException {
        collectionsHost = config.getInitParameter("waybackHost");
        solrHost = config.getInitParameter("solrServer");
        solrCollection = config.getInitParameter("solrCollection");

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

        solr = createSolr(solrHost, solrCollection);

    }


    /**
     * HttpServlet doGet method
     *
     * @param request  - type HttpServletRequest
     * @param response - type HttpServletResponse
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long startTime;
        long endTime;
        long duration;
        startTime = System.currentTimeMillis();

        LOG.debug("[doGet] query request from " + request.getRemoteAddr());

        ArrayList<String> fqStrings = new ArrayList<>();
        ArrayList<Map.Entry<String, SolrQuery.ORDER>> sortStrings = new ArrayList<>();
        String q = "";

        String requestURL = request.getScheme() + "://" +
                request.getServerName() +
                ("http".equals(request.getScheme()) && request.getServerPort() == 80 || "https".equals(request.getScheme()) && request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) +
                request.getRequestURI() +
                (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        LOG.debug("[imagesearch request] : " + requestURL);

        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null)
            ipAddress = request.getRemoteAddr();

        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.trim().isEmpty())
            userAgent = "-";
        else {
            userAgent = "\"" + userAgent + "\"";
        }

        LOG.info("request\t" + ipAddress + "\t" + userAgent + "\t" + requestURL);

        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD");

        int start = 0;
        int limit = 50; /*Default number of results*/

        Object imgSearchResponse = null;
        ImageSearchResults imgSearchResults = null;
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
        start = getResultsStart(startString, start);

        // number of items to display
        String limitString = request.getParameter("maxItems");
        limit = getLimit(limitString, limit);

        String dateStart = request.getParameter("from");
        String dateEnd = request.getParameter("to");
        parseDates(dateStart, dateEnd, fqStrings);

        String safeSearch = request.getParameter("safeSearch");
        parseSafeSearch(safeSearch, fqStrings);

        addBlockFilter(fqStrings);

        String typeParameter = request.getParameter("type");
        parseMimeType(typeParameter, fqStrings);

        String sizeParameter = request.getParameter("size");
        parseSizes(sizeParameter, fqStrings);

        if (request.getParameter("more") != null) {
            flString += request.getParameter("more") + ",";
        }

        if (request.getParameter("fields") != null) {
            flString += request.getParameter("fields");
        } else { //default params
            flString += V1_DEFAULT_FL_STRING;
        }

        StringBuilder V2Builder = new StringBuilder();
        for (String field : flString.split(","))
            V2Builder.append(APIVersionTranslator.v1Tov2(field)).append(",");
        // We always want URL and timestamp:
        if(V2Builder.indexOf(V2_IMAGEURL) < 0)
            V2Builder.append(V2_IMAGEURL).append(",");
        if(V2Builder.indexOf(V2_IMAGETSTAMP) < 0)
            V2Builder.append(V2_IMAGETSTAMP).append(",");
        if(V2Builder.indexOf(V2_PAGEURL) < 0)
            V2Builder.append(V2_PAGEURL).append(",");
        if(V2Builder.indexOf(V2_PAGETSTAMP) < 0)
            V2Builder.append(V2_PAGETSTAMP).append(",");
            
        String flStringV2 = V2Builder.toString();

        String siteSearch = request.getParameter("siteSearch");
        parseSiteFilter(siteSearch, fqStrings);

        String requestedCollection = request.getParameter("collection");
        parseCollectionFilter(requestedCollection, fqStrings);

        /*Process operators such as site: type: and site: inside the q parameter*/
        /*Should we allow people to use those operators when calling the api e.g.
         * /imagesearch?q=sapo%20site:sapo.pt%20type:jpeg instead of
         * /imagesearch?q=sapo&siteSearch=sapo.pt&type=jpeg */
        q = checkSpecialOperators(q, fqStrings);
        q = checkSortOperator(q, sortStrings);
        //Pretty print in output message
        String prettyPrintParameter = request.getParameter("prettyPrint");
        boolean prettyOutput = false;
        if (prettyPrintParameter != null && prettyPrintParameter.equals("true"))
            prettyOutput = true;


        //execute the query

        LinkedList<String> docIds = new LinkedList<>();

        try {
            LOG.debug("Wayback HOST: " + collectionsHost);
            LOG.debug("SOLR HOST: " + solrHost);


            SolrQuery solrQuery = new SolrQuery();

            if (q.trim().isEmpty()) {
                q = "*:*";
            }

            solrQuery.setQuery(q);
            LOG.debug("FilterQuery Strings:" + fqStrings);

            for (String fq : fqStrings) {
                solrQuery.addFilterQuery(fq);
            }

            addScoring(solrQuery, q);

            solrQuery.setRows(limit);
            solrQuery.setStart(start);
            solrQuery.set("fl", flStringV2);

            addSort(sortStrings, solrQuery);

            LOG.debug("SOLR Query: " + solrQuery);

            QueryResponse responseSolr = null;

            responseSolr = solr.query(solrQuery);

            LOG.debug("SOLR Query Done");

            SolrDocumentList documents = new SolrDocumentList();
            documents.addAll(responseSolr.getResults());
            for (SolrDocument doc : documents) {
                docIds.add(V1_DATE_FORMAT.format(doc.getFieldValue(V2_IMAGETSTAMP)) + "/" + doc.getFieldValue("imgUrl"));
            }

            int numFound = (int) responseSolr.getResults().getNumFound();

            int offsetPreviousPage = getOffsetPreviousPage(start, limit);
            String previousPage = requestURL.replaceAll("&offset=([^&]+)", "").concat("&offset=" + offsetPreviousPage);

            int offsetNextPage = getOffsetNextPage(start, limit, numFound);
            String nextPage = requestURL.replaceAll("&offset=([^&]+)", "").concat("&offset=" + offsetNextPage);

            String linkToMoreFields = requestURL.replaceAll("&more=([^&]+)", "").concat("&more=" + V1_MOREFIELDS);

            imgSearchResults = new ImageSearchResults(flString.split(","), numFound, documents.size(), responseSolr.getResults().getStart(), linkToMoreFields, nextPage, previousPage, documents, prettyOutput);
            
            if (request.getParameter("debug") != null && request.getParameter("debug").equals("on")) {
                imgSearchResponse = new ImageSearchResponseDebug(responseSolr.getResponseHeader(), imgSearchResults);
            } else {
                imgSearchResponse = imgSearchResults;
            }
            //} catch (IOException | HttpSolrClient.RemoteSolrException | SolrServerException e) {
            //    LOG.error(e.getClass().getCanonicalName(), e);
            //    imgSearchResponse = new ImageSearchErrorResponse(e);
        } catch (Throwable e) {
            LOG.error(e.getClass().getCanonicalName(), e);
            imgSearchResponse = new ImageSearchErrorResponse(e);
            response.setStatus(500);
        }
        /*finally {
            if (solr != null)
                solr.close();
        }
         */




        try {
            Gson gson = null;
            if (prettyOutput) {
                gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
            } else {
                gson = new GsonBuilder().disableHtmlEscaping().create();
            }
            jsonSolrResponse = gson.toJson(imgSearchResponse);

        } catch (JsonParseException e) {
            throw new ServletException(e);
        }

        response.setContentType("application/json"); //json

        response.setCharacterEncoding("UTF-8");

        // Get the printwriter object from response to write the required json object to the output stream
        PrintWriter out = response.getWriter();
        out.println(jsonSolrResponse);
        out.flush();

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        String docIdsJSON = gson.toJson(docIds);

        endTime = System.currentTimeMillis();
        duration = (endTime - startTime);


        String jsonString = gson.toJson(request.getParameterMap());

        LOG.info(ipAddress + "\t" + userAgent + "\t" + requestURL + "\t" + duration + "ms\tsearch_parameters:" + jsonString + "\tsearch_results:" + docIdsJSON);

    }

    private int getOffsetNextPage(int start, int limit, int numFound) {
        int offsetNextPage = start + limit;
        if (offsetNextPage > numFound) {
            offsetNextPage = numFound;
        }
        return offsetNextPage;
    }

    private int getOffsetPreviousPage(int start, int limit) {
        int offsetPreviousPage;
        if (start == 0)
            offsetPreviousPage = 0;
        else {
            offsetPreviousPage = start - limit;
            offsetPreviousPage = Math.max(offsetPreviousPage, 0);
        }
        return offsetPreviousPage;
    }

    private void parseSafeSearch(String safeSearch, ArrayList<String> fqStrings) {
        if (!"off".equalsIgnoreCase(safeSearch)) {
            fqStrings.add("safe:[0 TO 0.49]"); /*Default behaviour is to limit safe score from 0 -> 0.49; else show all images*/
        }
    }

    private void addBlockFilter(ArrayList<String> fqStrings) {
        fqStrings.add("blocked:0");
        fqStrings.add("isInline:false");
    }

    private int getLimit(String limitString, int limit) {
        if (limitString != null)
            limit = parseToIntWithDefault(limitString, 50);

        if (limit < 0)
            limit = 0;

        if (limit > 200)
            limit = 200; //Max Number of Results 200 in one request?
        return limit;
    }

    private int getResultsStart(String startString, int start) {
        if (startString != null)
            start = parseToIntWithDefault(startString, 0);
        return start;
    }

    private void addSort(ArrayList<Map.Entry<String, SolrQuery.ORDER>> sortStrings, SolrQuery solrQuery) {
        if (sortStrings.isEmpty()) {
            solrQuery.addSort("score", SolrQuery.ORDER.desc);
            solrQuery.addSort(V2_IMAGETSTAMP, SolrQuery.ORDER.asc);
            solrQuery.addSort(V2_IMAGEURL, SolrQuery.ORDER.asc);
        } else {
            for (Map.Entry<String, SolrQuery.ORDER> e : sortStrings)
                solrQuery.addSort(e.getKey(), e.getValue());
        }
    }

    private void addScoring(SolrQuery solrQuery, String q) {

        solrQuery.set("defType", "edismax");

        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, Integer> entry : DEFAULT_QUERY_FIELDS.entrySet())
            qs.append(String.format("%s^%d ", entry.getKey(), entry.getValue()));
        solrQuery.set("qf", qs.toString());


        qs = new StringBuilder();
        for (Map.Entry<String, Integer> entry : DEFAULT_QUERY_FIELDS.entrySet())
            qs.append(String.format("%s^%d ", entry.getKey(), entry.getValue() * 1000));

        solrQuery.set("pf", qs.toString());
        solrQuery.set("ps", 1);

        qs = new StringBuilder();
        for (Map.Entry<String, Integer> entry : DEFAULT_QUERY_FIELDS.entrySet())
            qs.append(String.format("%s^%d ", entry.getKey(), entry.getValue() * 100));

        solrQuery.set("pf2", qs.toString());
        solrQuery.set("ps2", 2);

        qs = new StringBuilder();
        for (Map.Entry<String, Integer> entry : DEFAULT_QUERY_FIELDS.entrySet())
            qs.append(String.format("%s^%d ", entry.getKey(), entry.getValue() * 10));

        solrQuery.set("pf3", qs.toString());
        solrQuery.set("ps3", 3);
    }

    private SolrClient createSolr(String solrHost, String solrCollection) {
        SolrClient solr = null;
        if (solrHost.contains(",")) {
            final List<String> zkServers = Arrays.asList(solrHost.split(","));
            solr = new CloudSolrClient.Builder(zkServers, Optional.of("/solr")).build();
            ((CloudSolrClient) solr).setDefaultCollection(solrCollection);
        } else {
            solr = new HttpSolrClient.Builder(solrHost + solrCollection).build();
        }
        return solr;
    }

    private void parseCollectionFilter(String requestedCollection, ArrayList<String> fqStrings) {
        if (requestedCollection != null && !requestedCollection.isEmpty()) {
            fqStrings.add(Arrays.stream(requestedCollection.split(",")).map(c -> "collection:" + c).collect(Collectors.joining(" OR ")));
        }
    }

    private void parseSiteFilter(String siteSearch, ArrayList<String> fqStrings) {
        if (siteSearch != null) {
            StringBuilder domainsFilter = new StringBuilder();
            for (String domainUnescaped : siteSearch.split(",")) {
                String domain = ClientUtils.escapeQueryChars(domainUnescaped);
                // unescape *, as it is needed to match all subdomains
                // https://github.com/arquivo/pwa-technologies/issues/1014
                // https://github.com/arquivo/pwa-technologies/issues/987
                domain = domain.replace("\\*", "*");
                if (!domain.isEmpty()) {
                    if (domainsFilter.length() != 0)
                        domainsFilter.append(" OR ");
                    domainsFilter.append("pageHost:");
                    domainsFilter.append(domain);
                }
            }
            fqStrings.add(domainsFilter.toString());
        }
    }

    private void parseMimeType(String typeParameter, ArrayList<String> fqStrings) {
        if (typeParameter != null && !typeParameter.isEmpty()) {
            typeParameter = typeParameter.replaceAll("jpg", "jpeg");
            fqStrings.add(Arrays.stream(typeParameter.split(",")).map(c -> "imgMimeType: image/" + c).collect(Collectors.joining(" OR ")));
        }
    }

    private void parseSizes(String sizeParameter, ArrayList<String> fqStrings) {
        if (sizeParameter != null && !sizeParameter.equals("")) {
            switch (sizeParameter) {
                case "sm":
                    fqStrings.add("{!frange u=65536 }product(imgHeight,imgWidth)"); /*images up to 65536pixels² of area - i.e. max square size of 256x256px*/
                    break;
                case "md":
                    fqStrings.add("{!frange l=65537 u=810000 }product(imgHeight,imgWidth)"); /*images between 65537pixels² of area , up to  810000px² of area - i.e. max square size of 900x900px*/
                    break;
                case "lg":
                    fqStrings.add("{!frange l=810001}product(imgHeight,imgWidth)"); /*images bigger than 810000px² of area*/
                    break;
            }
        }
    }

    private void parseDates(String dateStart, String dateEnd, ArrayList<String> fqStrings) {
        // date restriction
        SimpleDateFormat V1_DATE_FORMAT = (SimpleDateFormat) APIVersionTranslator.V1_DATE_FORMAT.clone();
        SimpleDateFormat V2_DATE_FORMAT = (SimpleDateFormat) APIVersionTranslator.V2_DATE_FORMAT.clone();

        /*
        if (dateStart == null || dateStart.length() == 0) {
            dateStart = "1996-01-01T00:00:00Z";
            //dateStart = FORMAT_OUT.format( dateStart );
        }

        if (dateEnd == null || dateEnd.length() == 0) {
            Calendar dateEND = currentDate();
            dateEnd = V2_DATE_FORMAT.format(dateEND.getTime());
        }
        */

        if (dateStart == null && dateEnd == null)
            return;

        if (dateStart != null && dateEnd != null) { //Logic to accept pages with yyyy and yyyyMMddHHmmss format

            try {
                V2_DATE_FORMAT.setLenient(false);
                DateFormat dOutputFormatYear = new SimpleDateFormat("yyyy");
                dOutputFormatYear.setLenient(false);
                if (tryParse(V1_DATE_FORMAT, dateStart)) {
                    Date dStart = V1_DATE_FORMAT.parse(dateStart);
                    dateStart = V2_DATE_FORMAT.format(dStart.getTime());
                } else if (tryParse(V1_DATE_FORMAT, dateStart + "0101000000")) {
                    Date dStart = V1_DATE_FORMAT.parse(dateStart + "0101000000");
                    dateStart = V2_DATE_FORMAT.format(dStart.getTime());
                } else {
                    dateStart = "1996-01-01T00:00:00Z";
                }

                if (tryParse(V1_DATE_FORMAT, dateEnd)) {
                    Date dEnd = V1_DATE_FORMAT.parse(dateEnd);
                    dateEnd = V2_DATE_FORMAT.format(dEnd.getTime());
                } else if (tryParse(V1_DATE_FORMAT, dateEnd + "1231235959")) {
                    Date dEnd = V1_DATE_FORMAT.parse(dateEnd + "1231235959");
                    dateEnd = V2_DATE_FORMAT.format(dEnd.getTime());
                } else {
                    Calendar dateEND = currentDate();
                    dateEnd = V2_DATE_FORMAT.format(dateEND.getTime());
                }
            } catch (ParseException e) {
                LOG.error("Parse Exception: ", e);
            } catch (IndexOutOfBoundsException e) {
                LOG.error("Parse Exception: ", e);
            }
        }
        fqStrings.add(V2_IMAGETSTAMP + ":[" + dateStart + " TO " + dateEnd + "]");
    }


    /*************************************************************/
    /********************* AUXILIARY METHODS *********************/
    /************************************************************/


    private String checkSpecialOperators(String q, ArrayList<String> fqStrings) {
        LOG.debug("checking special operators");
        if (q.contains("fq:") || q.contains("site:") || q.contains("type:") || q.contains("safe:") || q.contains("size:") || q.contains("collapse:")) { /*query has a special operator we need to deal with it*/
            LOG.debug("found special operator");
            String[] words = q.split(" ");
            ArrayList<String> cleanWords = new ArrayList<>();
            for (String word : words) {
                if (word.toLowerCase().startsWith("site:")) {
                    LOG.debug("found site:");
                    String domains = ClientUtils.escapeQueryChars(word.replace("site:", ""));
                    parseSiteFilter(domains, fqStrings);
                } else if (word.toLowerCase().startsWith("type:")) {
                    LOG.debug("found type:");
                    String typeWord = word.replace("type:", "");
                    parseMimeType(typeWord, fqStrings);
                } else if (word.toLowerCase().startsWith("safe:")) {
                    LOG.debug("found safe:");
                    String safeWord = word.replace("safe:", "");

                    if (safeWord.equalsIgnoreCase("off") || safeWord.equalsIgnoreCase("on")) {
                        removeMatchingFqString("safe", fqStrings);
                    }

                    parseSafeSearch(safeWord, fqStrings);
                } else if (word.toLowerCase().startsWith("size:")) {
                    LOG.debug("found size:");
                    String sizeWord = word.replace("size:", "").toLowerCase();
                    parseSizes(sizeWord, fqStrings);
                } else if (word.toLowerCase().startsWith("collapse:")) {
                    LOG.debug("found collapse:");
                    //fqStrings.remove("{!collapse field=imgDigest}");
                    String collapse = ClientUtils.escapeQueryChars(word.replace("collapse:", ""));
                    fqStrings.add(String.format("{!collapse field=%s}", collapse));
                } else if (word.toLowerCase().startsWith("fq:")) {
                    LOG.debug("found fq:");
                    String filterWords = word.replace("fq:", "");
                    filterWords = filterWords.replace("_", " ");
                    String[] filterWordTokens = filterWords.split(";");
                    for (String filterWordToken : filterWordTokens) {
                        removeMatchingFqString(filterWordToken.split(":")[0], fqStrings);
                        fqStrings.add(filterWordToken);
                    }
                } else {
                    LOG.debug(" found clean word");
                    cleanWords.add(word);
                }
            }
            return String.join(" ", cleanWords);
        } else return q;
    }

    private String checkSortOperator(String q, ArrayList<Map.Entry<String, SolrQuery.ORDER>> sortStrings) {
        LOG.debug("checking sort operators");
        if (q.contains("sort:")) {
            LOG.debug("found sort operator");
            String[] words = q.split(" ");
            ArrayList<String> cleanWords = new ArrayList<String>();
            for (String word : words) {
                //TODO: validate user input
                if (word.toLowerCase().startsWith("sort:")) {
                    LOG.debug("found sort:");
                    String[] sortInstances = ClientUtils.escapeQueryChars(word.replace("sort:", "")).split("\\\\;");
                    for (String instance : sortInstances) {
                        String[] sortInstance = instance.split(",");
                        String field = sortInstance[0];

                        String[] fieldInstances = field.split("\\\\\\^");
                        if (fieldInstances.length > 1) {
                            field = "pow(" + String.join(",",fieldInstances) + ")";
                        }
                        fieldInstances = field.split("\\\\\\*");
                        if (fieldInstances.length > 1) {
                            field = "product(" + String.join(",",fieldInstances) + ")";
                        }
                        fieldInstances = field.split("\\\\/");
                        if (fieldInstances.length > 1) {
                            field = "div(" + String.join(",",fieldInstances) + ")";
                        }
                        fieldInstances = field.split("\\\\\\+");
                        if (fieldInstances.length > 1) {
                            field = "sum(" + String.join(",",fieldInstances) + ")";
                        }
                        fieldInstances = field.split("\\\\-");
                        if (fieldInstances.length > 1) {
                            field = "sub(" + String.join(",",fieldInstances) + ")";
                        }

                        String dir = sortInstance[1];
                        sortStrings.add(new AbstractMap.SimpleEntry<>(field, SolrQuery.ORDER.valueOf(dir)));
                    }
                } else {
                    LOG.debug(" found clean word");
                    cleanWords.add(word);
                }
            }
            return String.join(" ", cleanWords);
        } else return q;
    }


    private void removeMatchingFqString(String field, ArrayList<String> fqStrings) {
        for (int i = 0; i < fqStrings.size(); i++) {
            if (fqStrings.get(i).startsWith(field)) {
                fqStrings.remove(i);
                i--;
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
        DateFormat df2 = (DateFormat) df.clone();
        boolean valid = false;
        try {
            df2.parse(s);
            valid = true;
        } catch (ParseException e) {
            valid = false;
        }
        return valid;
    }


    /**
     * Returns the current arquivo image search default end date (the last day of the year)
     *
     * @return current date in the format (YYYYMMDDHHMMSS)
     */
    private static Calendar currentDate() {
        //This is initialized using current time to avoid building another Calendar object to get the current year
        Calendar DATE_END = new GregorianCalendar();
        DATE_END.set(Calendar.YEAR, DATE_END.get(Calendar.YEAR));
        DATE_END.set(Calendar.MONTH, 12 - 1);
        DATE_END.set(Calendar.DAY_OF_MONTH, 31);
        DATE_END.set(Calendar.HOUR_OF_DAY, 23);
        DATE_END.set(Calendar.MINUTE, 59);
        DATE_END.set(Calendar.SECOND, 59);
        // If you forget the milliseconds, you are going to have a bad time
        DATE_END.set(Calendar.MILLISECOND, 0);
        return DATE_END;
    }

}