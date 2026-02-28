package ua.edu.university.util;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileUtil {
    public static String generateReportPath(String title) {
        File dir = new File("Reports");
        if (!dir.exists()) dir.mkdirs();

        String cleanTitle = title.replaceAll("[^a-zA-Z0-9а-яА-Я]", "_");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        return "Reports/Zvit_" + cleanTitle + "_" + ts + ".pdf";
    }
}
