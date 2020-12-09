package pt.arquivo;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

public class APIVersionTranslator {

    public static final HashMap<String,String> V1_TO_V2_MAP = new HashMap<String, String>() {
        {
            {
                put("imgSrc", "imgUrl");
                put("pageURL", "pageUrl");
                put("imgTstamp", "imgCrawlTimestamp");
                put("pageTstamp", "pageCrawlTimestamp");
                put("pageImages", "imagesInPage");
                put("imgThumbnailBase64", "imgSrcBase64");
            }
        }
    };

    public static final HashMap<String,String> V2_TO_V1_MAP = new HashMap<String, String>() {
        {
            for (Entry<String, String> keyValue: V1_TO_V2_MAP.entrySet()){
                put(keyValue.getValue(),keyValue.getKey());
            }
        }
    };

    private static final TimeZone TIMEZONE = TimeZone.getTimeZone("GMT");

    public static final SimpleDateFormat V1_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss") {
        {
            setTimeZone(TIMEZONE);
        }
    };

    public static final SimpleDateFormat V2_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US){
        {
            setTimeZone(TIMEZONE);
        }
    };



    public static String v1Tov2(String field){
        return V1_TO_V2_MAP.getOrDefault(field, field);
    }

    public static String v2Tov1(String field){
        return V2_TO_V1_MAP.getOrDefault(field, field);
    }


}
