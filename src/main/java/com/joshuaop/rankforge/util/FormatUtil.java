package com.joshuaop.rankforge.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shared formatting utilities — eliminates duplicated logic across managers and GUIs.
 */
public final class FormatUtil {

    private FormatUtil() {}

    /**
     * Format a duration in minutes as a human-readable string.
     * Examples: 45 → "45min", 90 → "1h 30min", 120 → "2h"
     */
    public static String formatTime(long minutes) {
        if (minutes < 60) return minutes + "min";
        long hours = minutes / 60;
        long rem   = minutes % 60;
        return rem > 0 ? hours + "h " + rem + "min" : hours + "h";
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
