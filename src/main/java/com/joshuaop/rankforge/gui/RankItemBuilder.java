package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds ItemStacks for rank entries and GUI decoration (glass panes).
 *
 * Status icon convention used across all rank GUIs:
 *   ✔  — Completed / already unlocked
 *   ⏳  — Current rank
 *   ➤  — Next rank (reachable / in progress)
 *   🔒  — Locked (future rank)
 */
public final class RankItemBuilder {

    private RankItemBuilder() {}

    /**
     * Build a rank item for the animated rank tree GUI.
     *
     * @param rank        Rank model data
     * @param isCurrent   True if this is the player's active rank
     * @param isUnlocked  True if the player has already passed this rank
     */
    public static ItemStack build(RankModel rank, boolean isCurrent, boolean isUnlocked) {
        Material mat = isCurrent ? Material.LIME_WOOL : parseMaterial(rank.getMaterial());

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        String statusPrefix;
        if (isCurrent)       statusPrefix = "§a⏳ ";
        else if (isUnlocked) statusPrefix = "§e✔ ";
        else                 statusPrefix = "§8🔒 ";

        meta.setDisplayName(statusPrefix + rank.getDisplayName());

        List<String> lore = new ArrayList<>(rank.getLore());

        lore.add("§8§m──────────────────────────");

        if (isCurrent) {
            lore.add("§a§l⏳ Current Rank");
            lore.add("§7Open this GUI to view progress");
            lore.add("§7toward the next rank.");
        } else if (isUnlocked) {
            lore.add("§a§l✔ Completed");
            lore.add("§7You have already unlocked");
            lore.add("§7this rank.");
        } else {
            lore.add("§c§l🔒 Locked");
            lore.add("§7Complete earlier ranks first");
            lore.add("§7to unlock this one.");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Build a rank item for the next-rank slot, showing that it is reachable.
     * Player-specific requirement details are appended by AnimatedRankTreeGUI.
     *
     * @param rank       The target rank model
     * @param canRankUp  True if all requirements are currently met
     */
    public static ItemStack buildNext(RankModel rank, boolean canRankUp) {
        Material mat = parseMaterial(rank.getMaterial());
        if (canRankUp) mat = Material.EMERALD_BLOCK;

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§e➤ " + rank.getDisplayName());

        List<String> lore = new ArrayList<>(rank.getLore());
        lore.add("§8§m──────────────────────────");
        lore.add(canRankUp ? "§a§l✔ Requirements met!" : "§e§l➤ Next Rank (in progress)");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Create a named glass pane for GUI borders/decoration. */
    public static ItemStack glassPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Create a named filler item with a custom label. */
    public static ItemStack filler(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material parseMaterial(String name) {
        try { return Material.valueOf(name); }
        catch (IllegalArgumentException e) { return Material.GRAY_WOOL; }
    }
}
