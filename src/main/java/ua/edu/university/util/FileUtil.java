package ua.edu.university.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Утилітарний клас для роботи з файловою системою.
 * Забезпечує створення структури папок та генерацію унікальних імен для PDF-звітів.
 */
public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    /**
     * Генерує унікальний шлях до файлу звіту на основі введеної назви та поточної дати.
     *
     * @param title Назва ділянки або адреса (буде очищена від заборонених символів)
     * @return Повний відносний шлях до файлу .pdf
     */
    public static String generateReportPath(String title) {
        String baseDir = ConfigManager.getProperty("reports.directory");
        String prefix = ConfigManager.getProperty("reports.file.prefix");
        String dateFormat = ConfigManager.getProperty("reports.date.format");

        File dir = new File(baseDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("Створено нову директорію для звітів: {}", baseDir);
            }
        }
        String cleanTitle = title.replaceAll("[^a-zA-Z0-9а-яА-ЯіїєґІЇЄҐ]", "_");

        if (cleanTitle.length() > 50) {
            cleanTitle = cleanTitle.substring(0, 50);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(dateFormat));
        String fullPath = String.format("%s/%s%s_%s.pdf", baseDir, prefix, cleanTitle, timestamp);
        logger.debug("Сгенеровано шлях для нового звіту: {}", fullPath);
        return fullPath;
    }
}