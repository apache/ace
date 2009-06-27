package net.luminis.liq.ma.webstart;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Properties;

public class Override {
    private static Properties props = null;

    public static String getProperty(String key) throws NoSuchElementException {
        if (props == null) {
            File appHome = new File(System.getProperty("application.home", System.getProperty("user.home")));
            File configFile = new File(appHome, "config.properties");
            if (configFile.isFile()) {
                props = new Properties();
                try {
                    props.load(new FileInputStream(configFile));
                }
                catch (IOException e) {
                    props = null;
                }
            }
        }
        if (props != null) {
            String value = props.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        throw new NoSuchElementException();
    }
}
