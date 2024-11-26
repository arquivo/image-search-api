package pt.arquivo;

import java.io.FileInputStream;
import java.util.Properties;

// Class to fet the configurations stored in application.properties
public class ImageSearchProperties {
    private static Properties configs = null;

    private static Properties getConfigs() {
        if(configs == null) {
            String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
            String appConfigPath = rootPath + "application.properties";

            configs = new Properties();
            try {
                configs.load(new FileInputStream(appConfigPath));
            } catch (Exception e) {
                // Fail-safe properties 
                configs.setProperty("linkToService", "https://arquivo.pt/images.jsp");
                configs.setProperty("waybackAddress", "https://arquivo.pt/wayback/");

                e.printStackTrace();
            }
        } 
        return configs;
    }
    
    public static String get(String key){
        return getConfigs().getProperty(key);
    }
}
