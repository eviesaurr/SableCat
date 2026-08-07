package eviesaurr.sablecat;

import com.google.gson.*;
import eviesaurr.sablecat.fix.FixEntry;
import eviesaurr.sablecat.fix.FixRegistry;
import eviesaurr.sablecat.i18n.LanguageManager;

import java.io.*;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SableCatConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String language = "en";
    private boolean autoUpdate = false;
    private final Map<String, Boolean> fixes = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> fixParams = new LinkedHashMap<>();
    private boolean existedOnDisk = false;

    private SableCatConfig() {}

    public static SableCatConfig load(Path configDir) {
        Path configPath = configDir.resolve("sablecat").resolve("config.json");
        SableCatConfig config = new SableCatConfig();
        config.existedOnDisk = Files.exists(configPath);
        if (config.existedOnDisk) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (obj.has("language")) {
                    config.language = obj.get("language").getAsString();
                }
                if (obj.has("autoUpdate")) {
                    config.autoUpdate = obj.get("autoUpdate").getAsBoolean();
                }
                if (obj.has("fixes")) {
                    JsonObject fixesObj = obj.getAsJsonObject("fixes");
                    for (Map.Entry<String, JsonElement> entry : fixesObj.entrySet()) {
                        config.fixes.put(entry.getKey(), entry.getValue().getAsBoolean());
                    }
                }
                if (obj.has("fixParams")) {
                    JsonObject paramsObj = obj.getAsJsonObject("fixParams");
                    for (Map.Entry<String, JsonElement> entry : paramsObj.entrySet()) {
                        Map<String, Object> params = new LinkedHashMap<>();
                        JsonObject fixParamsObj = entry.getValue().getAsJsonObject();
                        for (Map.Entry<String, JsonElement> param : fixParamsObj.entrySet()) {
                            JsonElement val = param.getValue();
                            if (val.isJsonPrimitive()) {
                                JsonPrimitive prim = val.getAsJsonPrimitive();
                                if (prim.isNumber()) {
                                    params.put(param.getKey(), prim.getAsNumber());
                                } else if (prim.isBoolean()) {
                                    params.put(param.getKey(), prim.getAsBoolean());
                                } else {
                                    params.put(param.getKey(), prim.getAsString());
                                }
                            }
                        }
                        config.fixParams.put(entry.getKey(), params);
                    }
                }
            } catch (Exception e) {
                SableCat.LOGGER.warn("Failed to load config, using defaults", e);
            }
        }
        return config;
    }

    public void save(Path configDir) {
        Path configPath = configDir.resolve("sablecat").resolve("config.json");
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("language", LanguageManager.getCurrentLanguage());
            obj.addProperty("autoUpdate", autoUpdate);
            JsonObject fixesObj = new JsonObject();
            for (FixEntry entry : FixRegistry.getAllFixes()) {
                fixesObj.addProperty(entry.getId(), entry.isExplicitlyEnabled());
            }
            obj.add("fixes", fixesObj);
            JsonObject paramsObj = new JsonObject();
            for (FixEntry entry : FixRegistry.getAllFixes()) {
                Map<String, Object> opts = entry.getOptions();
                if (opts.isEmpty()) continue;
                JsonObject fixParamsObj = new JsonObject();
                for (Map.Entry<String, Object> opt : opts.entrySet()) {
                    Object val = opt.getValue();
                    if (val instanceof Number) {
                        fixParamsObj.addProperty(opt.getKey(), (Number) val);
                    } else if (val instanceof Boolean) {
                        fixParamsObj.addProperty(opt.getKey(), (Boolean) val);
                    } else {
                        fixParamsObj.addProperty(opt.getKey(), String.valueOf(val));
                    }
                }
                paramsObj.add(entry.getId(), fixParamsObj);
            }
            obj.add("fixParams", paramsObj);
            Files.writeString(configPath, GSON.toJson(obj));
        } catch (IOException e) {
            SableCat.LOGGER.error("Failed to save config", e);
        }
    }

    public String getLanguage() { return language; }
    public boolean isAutoUpdate() { return autoUpdate; }
    public Map<String, Boolean> getFixStates() { return fixes; }
    public Map<String, Map<String, Object>> getFixParams() { return fixParams; }
    public boolean existedOnDisk() { return existedOnDisk; }
}
