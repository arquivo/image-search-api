package pt.arquivo;

import java.io.FileInputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Class to get the configurations stored in application.properties
public class ImageSearchProperties {
    private static Properties configs = null;
    private static final Logger LOG = LoggerFactory.getLogger(ImageSearchServlet.class);

    private static Properties getConfigs() {
        if(configs == null) {
            String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
            String appConfigPath = rootPath + "application.properties";

            configs = new Properties();
            try {
                configs.load(new FileInputStream(appConfigPath));
            } catch (Exception e) {
                configs.setProperty("linkToService", "https://arquivo.pt/images.jsp");
                configs.setProperty("waybackAddress", "https://arquivo.pt/wayback/");

                LOG.error(e.toString());
            }
        }
        return configs;
    }

    public static String get(String key){
        String envKey = key.replaceAll("([A-Z])", "_$1").toUpperCase();
        String envVal = System.getenv(envKey);
        if (envVal != null) return envVal;
        return getConfigs().getProperty(key);
    }
}
