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
        /** Player earned a rank-up via requirements. */
        RANKUP,
        /** An admin directly set the player's rank. */
        SET,
        /** The rank was reset to the server default. */
        RESET
    }

    /** 
     * Human-readable formatted date-time string.
     * Respects the server owner's configured timezone fallback.
     */
    public String getFormattedTime() {
        ZoneId zone = getConfiguredZoneId();
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone);
        return TIME_FORMATTER.format(dt);
    }

    /** Short display line for in-game history messages or GUI layouts. */
    public String toDisplayLine() {
        // Clean title-casing: e.g., "RANKUP" -> "Rankup", "RESET" -> "Reset"
        String typeName = type.name().charAt(0) + type.name().substring(1).toLowerCase();
        
        return "§8[" + getFormattedTime() + "§8] §7"
                + typeName + "§8: §e" + fromRankId + " §7→ §a" + toRankId;
    }

    /**
     * Helper to safely fetch the timezone specified in config.yml.
     * Falls back to the machine's system default if unconfigured or invalid.
     */
    private ZoneId getConfiguredZoneId() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("RankForge");
        if (plugin != null) {
            // Read from config key, e.g., timezone: "Asia/Manila" or "America/New_York"
            String zoneStr = plugin.getConfig().getString("timezone");
            if (zoneStr != null && !zoneStr.trim().isEmpty()) {
                try {
                    return ZoneId.of(zoneStr);
                } catch (Exception e) {
                    // Invalid ID specified in config, silently drop to default
                }
            }
        }
        return ZoneId.systemDefault();
    }
}
