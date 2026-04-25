package ua.edu.university.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Завантажує AI-промти з classpath-ресурсів {@code /prompts/<name>.md}.
 * Формат файлу: перший рядок {@code role: <текст>}, потім роздільник {@code ---},
 * потім тіло задачі з плейсхолдерами {@code %f} для lat/lon.
 * Результати кешуються в {@code ConcurrentHashMap} після першого читання.
 */
public class PromptLoader {
    private static final Logger logger = LoggerFactory.getLogger(PromptLoader.class);
    private static final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    public record PromptTemplate(String role, String task) {
    }

    public static PromptTemplate load(String name) {
        return cache.computeIfAbsent(name, PromptLoader::readFromResource);
    }

    private static PromptTemplate readFromResource(String name) {
        String path = "/prompts/" + name + ".md";
        try (InputStream is = PromptLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                logger.error("Prompt file not found: {}", path);
                return new PromptTemplate("Ти аналітик", "Проаналізуй координати %f, %f.");
            }
            String content = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            String[] parts = content.split("\n---\n", 2);
            String role = parts[0].replaceFirst("^role:\\s*", "").trim();
            String task = parts.length > 1 ? parts[1].trim() : "";
            return new PromptTemplate(role, task);
        } catch (Exception e) {
            logger.error("Failed to load prompt '{}': {}", name, e.getMessage());
            return new PromptTemplate("Ти аналітик", "Проаналізуй координати %f, %f.");
        }
    }
}
