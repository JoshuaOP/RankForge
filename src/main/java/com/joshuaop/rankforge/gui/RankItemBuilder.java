package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds ItemStacks for rank entries and GUI decoration (glass panes).
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

        String prefix = isCurrent ? "§a⟩ " : (isUnlocked ? "§e" : "§8");
        meta.setDisplayName(prefix + rank.getDisplayName());

        List<String> lore = new ArrayList<>(rank.getLore());
        lore.add("");
        if (isCurrent)       lore.add("§a✦ §lCurrent Rank");
        else if (isUnlocked) lore.add("§a✔ Completed");
        else                 lore.add("§c✘ Locked");

        if (!rank.getNextRankId().isBlank()) lore.add("§7Next: §e" + rank.getNextRankId());
        if (rank.getRequiredMoney()   > 0)   lore.add("§7Money: §a$" + (long) rank.getRequiredMoney());
        if (rank.getRequiredXpLevel() > 0)   lore.add("§7XP Level: §a" + rank.getRequiredXpLevel());

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
