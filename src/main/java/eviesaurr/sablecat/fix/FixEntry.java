package eviesaurr.sablecat.fix;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class FixEntry {

    public enum Side {
        SERVER,
        CLIENT,
        BOTH
    }

    private final String id;
    private final String description;
    private final boolean defaultEnabled;
    private final Set<String> requiredMods;
    private final Side side;
    private final boolean hidden;
    private boolean environmentMet;
    private boolean enabled;
    private final Map<String, Object> options;
    private final Map<String, Object> defaultOptions = new LinkedHashMap<>();

    FixEntry(String id, String description, boolean defaultEnabled, Set<String> requiredMods, Side side) {
        this(id, description, defaultEnabled, requiredMods, side, false);
    }

    FixEntry(String id, String description, boolean defaultEnabled, Set<String> requiredMods, Side side, boolean hidden) {
        this.id = id;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.requiredMods = requiredMods != null ? Set.copyOf(requiredMods) : Set.of();
        this.side = side != null ? side : Side.BOTH;
        this.hidden = hidden;
        this.environmentMet = true;
        this.enabled = defaultEnabled;
        this.options = new LinkedHashMap<>();
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public boolean isDefaultEnabled() { return defaultEnabled; }
    public Set<String> getRequiredMods() { return requiredMods; }
    public Side getSide() { return side; }
    public boolean isHidden() { return hidden; }
    public boolean isEnvironmentMet() { return environmentMet; }

    void setEnvironmentMet(boolean met) { this.environmentMet = met; }

    public boolean isEnabled() { return enabled && environmentMet && isSideMatch(); }

    private boolean isSideMatch() {
        if (side == Side.BOTH) return true;
        boolean isClient = net.neoforged.api.distmarker.Dist.CLIENT == net.neoforged.fml.loading.FMLLoader.getDist();
        if (side == Side.SERVER) return !isClient;
        if (side == Side.CLIENT) return isClient;
        return true;
    }

    public boolean isExplicitlyEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getOptions() { return Collections.unmodifiableMap(options); }
    public void setOption(String key, Object value) { options.put(key, value); }
    public Object getOption(String key) { return options.get(key); }
    @SuppressWarnings("unchecked")
    public <T> T getOption(String key, T defaultValue) {
        Object v = options.get(key);
        if (v == null) return defaultValue;
        try { return (T) v; } catch (ClassCastException e) { return defaultValue; }
    }

    public void setDefaultOption(String key, Object value) {
        defaultOptions.put(key, value);
        options.put(key, value);
    }

    public void resetOptions() {
        options.clear();
        options.putAll(defaultOptions);
    }

    public Map<String, Object> getDefaultOptions() { return Collections.unmodifiableMap(defaultOptions); }
}
