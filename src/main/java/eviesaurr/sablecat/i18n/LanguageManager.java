package eviesaurr.sablecat.i18n;

import eviesaurr.sablecat.SableCat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class LanguageManager {

    private static final String LANG_DIR_NAME = "lang";
    private static final String CONFIG_DIR_NAME = "sablecat";

    private static String currentLang = "en";
    private static Map<String, String> translations = new LinkedHashMap<>();
    private static Map<String, String> fallbackTranslations = new LinkedHashMap<>();
    private static Path langPath;
    private static final Set<String> availableLanguages = new LinkedHashSet<>();

    private static final Map<String, Map<String, String>> loadedPacks = new LinkedHashMap<>();

    private LanguageManager() {}

    public static void init(Path configDir) {
        langPath = configDir.resolve(CONFIG_DIR_NAME).resolve(LANG_DIR_NAME);

        try {
            Files.createDirectories(langPath);
        } catch (IOException e) {
            SableCat.LOGGER.error("Failed to create language directory", e);
        }

        extractAndVerify("en");
        extractAndVerify("zh");

        scanLanguageFiles();

        for (String lang : availableLanguages) {
            loadedPacks.put(lang, loadLanguageFile(lang));
        }

        fallbackTranslations = loadedPacks.getOrDefault("en", new LinkedHashMap<>());

        loadCurrentLanguage();

        SableCat.LOGGER.info("Language manager initialized. Current: {}, Available: {}", currentLang, availableLanguages);
    }

    private static void extractAndVerify(String lang) {
        String resourcePath = "/sablecat/lang/" + lang + ".yml";
        Path targetPath = langPath.resolve(lang + ".yml");

        try (InputStream embedded = LanguageManager.class.getResourceAsStream(resourcePath)) {
            if (embedded == null) {
                SableCat.LOGGER.warn("No embedded language pack found for: {}", lang);
                return;
            }

            String embeddedContent = new String(embedded.readAllBytes(), StandardCharsets.UTF_8);

            if (Files.exists(targetPath)) {
                String existingContent = Files.readString(targetPath, StandardCharsets.UTF_8);
                if (contentMatches(embeddedContent, existingContent)) {
                    return;
                }
                SableCat.LOGGER.info("Language pack {} is outdated, replacing with embedded version", lang);
            }

            Files.writeString(targetPath, embeddedContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            SableCat.LOGGER.info("Extracted language pack: {}", lang);

        } catch (IOException e) {
            SableCat.LOGGER.error("Failed to extract language pack: {}", lang, e);
        }
    }

    private static boolean contentMatches(String embedded, String existing) {
        return embedded.trim().equals(existing.trim());
    }

    static String extractVersion(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("_version:")) {
                String value = trimmed.substring("_version:".length()).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static void scanLanguageFiles() {
        availableLanguages.clear();
        if (!Files.isDirectory(langPath)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(langPath, "*.yml")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                availableLanguages.add(name.substring(0, name.length() - 4));
            }
        } catch (IOException e) {
            SableCat.LOGGER.error("Failed to scan language files", e);
        }
    }

    private static Map<String, String> loadLanguageFile(String lang) {
        Path file = langPath.resolve(lang + ".yml");
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> nested = SimpleYamlReader.read(reader);
            return SimpleYamlReader.flatten(nested);
        } catch (IOException e) {
            SableCat.LOGGER.error("Failed to load language file: {}", lang, e);
            return new LinkedHashMap<>();
        }
    }

    private static void loadCurrentLanguage() {
        translations = loadedPacks.getOrDefault(currentLang, new LinkedHashMap<>());
    }

    public static String get(String key) {
        String value = translations.get(key);
        if (value != null) return value;
        value = fallbackTranslations.get(key);
        return value != null ? value : key;
    }

    public static String get(String key, Object... args) {
        return String.format(get(key), args);
    }

    public static String getRaw(String lang, String key) {
        Map<String, String> pack = loadedPacks.get(lang);
        return pack != null ? pack.get(key) : null;
    }

    public static void setLanguage(String lang) {
        if (!availableLanguages.contains(lang)) {
            SableCat.LOGGER.warn("Language not available: {}", lang);
            return;
        }
        currentLang = lang;
        loadCurrentLanguage();
    }

    public static String getCurrentLanguage() { return currentLang; }
    public static Set<String> getAvailableLanguages() { return Collections.unmodifiableSet(availableLanguages); }
    public static Path getLangPath() { return langPath; }
}
