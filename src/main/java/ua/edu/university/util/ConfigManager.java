package ua.edu.university.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigManager.class.getResourceAsStream("/application.properties")) {
            if (input == null) {
                logger.error("Файл application.properties не знайдено!");
            } else {
                properties.load(input);
            }
        } catch (Exception e) {
            logger.error("Помилка завантаження конфігурації", e);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static double getDoubleProperty(String key) {
        return Double.parseDouble(properties.getProperty(key));
    }

    public static int getIntProperty(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public static boolean getBooleanProperty(String key) {
        String value = getProperty(key);
        return Boolean.parseBoolean(value);
    }
}