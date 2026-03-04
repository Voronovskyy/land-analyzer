package ua.edu.university.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Менеджер конфігурації системи.
 * Забезпечує доступ до параметрів у файлі application.properties.
 * Використовує статичну ініціалізацію для кешування налаштувань.
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE = "/application.properties";

    static {
        loadConfig();
    }

    /**
     * Завантажує параметри з файлу ресурсів.
     */
    private static void loadConfig() {
        try (InputStream input = ConfigManager.class.getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                logger.error("Критична помилка: Файл {} не знайдено в ресурсах!", CONFIG_FILE);
            } else {
                properties.load(input);
                logger.info("Конфігурацію успішно завантажено з {}. Завантажено ключів: {}",
                        CONFIG_FILE, properties.size());
            }
        } catch (Exception e) {
            logger.error("Збій при читанні файлу конфігурації", e);
        }
    }

    /**
     * Отримує рядкове значення за ключем.
     *
     * @param key Назва параметру
     * @return Значення або null, якщо ключ відсутній
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Отримує числове значення (double).
     * У разі помилки або відсутності ключа повертає 0.0.
     *
     * @param key Назва параметру
     */
    public static double getDoubleProperty(String key) {
        try {
            String value = getProperty(key);
            return value != null ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            logger.warn("Помилка формату числа для ключа {}. Повертаємо 0.0", key);
            return 0.0;
        }
    }

    /**
     * Отримує цілочисельне значення (int).
     *
     * @param key Назва параметру
     */
    public static int getIntProperty(String key) {
        try {
            String value = getProperty(key);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            logger.warn("Помилка формату цілого числа для ключа {}. Повертаємо 0", key);
            return 0;
        }
    }

    /**
     * Отримує логічне значення (boolean).
     *
     * @param key Назва параметру
     * @return true/false (за замовчуванням false, якщо ключ відсутній)
     */
    public static boolean getBooleanProperty(String key) {
        String value = getProperty(key);
        if (value == null) {
            logger.debug("Логічний параметр {} не знайдено. Використовуємо 'false'", key);
            return false;
        }
        return Boolean.parseBoolean(value.trim());
    }
}