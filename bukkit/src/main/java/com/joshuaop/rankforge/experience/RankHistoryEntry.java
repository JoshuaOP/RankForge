package com.joshuaop.rankforge.experience;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Immutable record representing a single rank-change event in a player's history.
 * Stored and loaded by {@link RankHistoryManager}.
 */
public record RankHistoryEntry(
        UUID   playerUuid,
        String playerName,
        String fromRankId,
        String toRankId,
        ChangeType type,
        long   timestamp
) {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * The reason a rank changed.
     */
    public enum ChangeType {
        RANKUP("Rankup"),
        SET("Set"),
        RESET("Reset");

        private final String displayName;

        ChangeType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** * Human-readable formatted date-time string.
     * Respects the server owner's configured timezone fallback.
     */
    public String getFormattedTime() {
        ZoneId zone = getConfiguredZoneId();
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone);
        return TIME_FORMATTER.format(dt);
    }

    /** Short display line for in-game history messages or GUI layouts. */
    public String toDisplayLine() {
        return "§8[" + getFormattedTime() + "§8] §7"
                + type.getDisplayName() + "§8: §e" + fromRankId + " §7→ §a" + toRankId;
    }

    /**
     * Helper to safely fetch the timezone specified in config.yml.
     * Falls back to the machine's system default if unconfigured, invalid, 
     * or called asynchronously during sensitive server states (e.g., shutdown).
     */
    private ZoneId getConfiguredZoneId() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("RankForge");
            if (plugin != null) {
                String zoneStr = plugin.getConfig().getString("timezone");
                if (zoneStr != null && !zoneStr.trim().isEmpty()) {
                    return ZoneId.of(zoneStr.trim());
                }
            }
        } catch (Exception e) {
            // Catches any IllegalArgumentException from bad timezones 
            // OR any thread access/null issues if accessed during server shutdown async tasks
        }
        return ZoneId.systemDefault();
    }
}
