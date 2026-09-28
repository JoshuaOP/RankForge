package com.joshuaop.rankforge.placeholder;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for resolving {rankforge_xxx} style placeholders in config strings.
 * Supports both %rankforge_xxx% (PAPI format) and {rankforge_xxx} (config format).
 */
public final class PlaceholderUtil {

    private PlaceholderUtil() {}

    /**
     * Replace all {key} and %key% tokens in a string with values from the provided map.
     * Keys must match without the enclosing braces/percents.
     */
    public static String resolve(String template, Map<String, String> values) {
        if (template == null || template.isEmpty() || values == null || values.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null) continue;
            String curly   = "{" + entry.getKey() + "}";
            String percent = "%" + entry.getKey() + "%";
            String val     = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(curly, val).replace(percent, val);
        }
        return result;
    }

    /**
     * Build the standard RankForge placeholder map for a context-free lookup
     * (values that don't require a player).
     */
    public static Map<String, String> systemPlaceholders(String version, String mcVersion,
                                                          boolean mcSupported) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("rankforge_version",              version != null ? version : "Unknown");
        placeholders.put("rankforge_mc_version",           mcVersion != null ? mcVersion : "Unknown");
        placeholders.put("rankforge_mc_version_supported", String.valueOf(mcSupported));
        placeholders.put("rankforge_gui_title",            "§8✦ §6RankForge §8✦");
        return placeholders;
    }
}
