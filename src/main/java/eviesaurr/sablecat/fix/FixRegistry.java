package eviesaurr.sablecat.fix;

import eviesaurr.sablecat.SableCat;

import java.util.*;

public final class FixRegistry {

    private static final Map<String, FixEntry> fixes = new LinkedHashMap<>();

    private FixRegistry() {}

    public static FixEntry register(String id, String description, boolean defaultEnabled, FixEntry.Side side) {
        return register(id, description, defaultEnabled, null, side);
    }

    public static FixEntry register(String id, String description, boolean defaultEnabled, Set<String> requiredMods, FixEntry.Side side) {
        return register(id, description, defaultEnabled, requiredMods, side, false);
    }

    public static FixEntry register(String id, String description, boolean defaultEnabled, Set<String> requiredMods, FixEntry.Side side, boolean hidden) {
        if (fixes.containsKey(id)) {
            throw new IllegalArgumentException("Fix already registered: " + id);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("Fix '" + id + "' must have a non-empty description");
        }
        FixEntry entry = new FixEntry(id, description, defaultEnabled, requiredMods, side, hidden);
        fixes.put(id, entry);
        SableCat.LOGGER.debug("Registered fix: {} (default: {}, side: {}, requiredMods: {}, hidden: {})", id, defaultEnabled, side, requiredMods, hidden);
        return entry;
    }

    public static void checkEnvironment(java.util.function.Function<String, Boolean> modChecker) {
        for (FixEntry entry : fixes.values()) {
            if (entry.getRequiredMods().isEmpty()) {
                entry.setEnvironmentMet(true);
                continue;
            }
            boolean met = true;
            for (String modId : entry.getRequiredMods()) {
                if (!modChecker.apply(modId)) {
                    met = false;
                    SableCat.LOGGER.info("Fix '{}' requires mod '{}' which is not loaded, fix disabled", entry.getId(), modId);
                    break;
                }
            }
            entry.setEnvironmentMet(met);
        }
    }

    public static boolean isEnabled(String id) {
        FixEntry entry = fixes.get(id);
        return entry != null && entry.isEnabled();
    }

    public static FixEntry getFix(String id) { return fixes.get(id); }

    public static Collection<FixEntry> getAllFixes() {
        return Collections.unmodifiableCollection(fixes.values());
    }
    
}
