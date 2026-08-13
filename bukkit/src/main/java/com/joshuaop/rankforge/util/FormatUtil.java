package com.joshuaop.rankforge.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared formatting utilities — eliminates duplicated logic across managers and GUIs.
 */
public final class FormatUtil {

    private FormatUtil() {}

    private static final Pattern PLAYTIME_TOKEN =
            Pattern.compile("(\\d+)\\s*(d|hr|m|s)", Pattern.CASE_INSENSITIVE);

    /**
     * Parses a playtime string such as {@code "5d 5hr 5m 5s"} into total minutes.
     *
     * <p>Supported units (case-insensitive): {@code d} = days, {@code hr} = hours,
     * {@code m} = minutes, {@code s} = seconds (truncated to whole minutes).
     * Also accepts a plain integer string as a raw minute count for backward compatibility.
     * Extra whitespace is ignored. Any combination of units is accepted.
     *
     * @param value the raw input string
     * @return total required playtime in whole minutes (≥ 0)
     * @throws IllegalArgumentException if the string is empty, contains only unrecognised
     *                                  characters, or produces a negative result
     */
    public static long parsePlaytimeString(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Playtime value must not be empty.");

        String trimmed = value.trim();

        // Accept a plain integer as a raw minute count (e.g. "60" → 60 minutes)
        try {
            long plain = Long.parseLong(trimmed);
            if (plain < 0) throw new IllegalArgumentException("Playtime must not be negative.");
            return plain;
        } catch (NumberFormatException ignored) {
            // Not a plain integer — continue to time-unit parsing below
        }

        // Validate: strip every recognised token; nothing significant should remain
        String remaining = PLAYTIME_TOKEN.matcher(trimmed).replaceAll("").trim();
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unrecognized characters: '" + remaining
                            + "'. Expected format: \"5d 5hr 5m 5s\"");
        }

        Matcher matcher = PLAYTIME_TOKEN.matcher(trimmed);
        long totalMinutes = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            switch (unit) {
                case "d":  totalMinutes += amount * 24L * 60L; break;
                case "hr": totalMinutes += amount * 60L;       break;
                case "m":  totalMinutes += amount;              break;
                case "s":  totalMinutes += amount / 60L;        break;
            }
        }

        if (!found)
            throw new IllegalArgumentException(
                    "No valid time units found. Expected format: \"5d 5hr 5m 5s\"");

        if (totalMinutes < 0)
            throw new IllegalArgumentException("Playtime must not be negative.");

        return totalMinutes;
    }

    /**
     * Format a duration in minutes as a human-readable string using the unified playtime format.
     * Examples: 45 → "45m", 90 → "1hr 30m", 1440 → "1d", 1500 → "1d 1hr", 5765 → "4d 0hr 5m"
     * Only non-zero components are included, except when all are zero (returns "0m").
     */
    public static String formatTime(long minutes) {
        if (minutes <= 0) return "0m";
        long days  = minutes / (24L * 60L);
        long hours = (minutes % (24L * 60L)) / 60L;
        long mins  = minutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (days  > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("hr ");
        if (mins  > 0) sb.append(mins).append("m");
        String result = sb.toString().trim();
        return result.isEmpty() ? "0m" : result;
    }

    /**
     * Format a Material enum name as Title Case with spaces.
     * Example: IRON_INGOT → "Iron Ingot"
     */
    public static String formatMaterial(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty())
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Count how many of a given Material the player holds across all inventory slots.
     */
    public static int countItem(Player player, Material mat) {
        int total = 0;
        if (player.getInventory() == null) return 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) total += item.getAmount();
        }
        return total;
    }
}
