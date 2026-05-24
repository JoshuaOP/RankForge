package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Main player-facing rank tree GUI.
 *
 * Layout (54 slots):
 *   Slot 4   — Player head info panel
 *   Slots 9-44 — Rank items at their configured slots
 *   Slot 49  — RankForge plugin info
 *   Row 1 + Row 6 — Cyan glass border (static)
 */
public class AnimatedRankTreeGUI {

    public static final String TITLE = "§8✦ §6RankForge §8✦";
    private static final Set<UUID> OPEN_VIEWERS = new HashSet<>();

    private final RankForge plugin;

    public AnimatedRankTreeGUI(RankForge plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        buildBorder(inv);
        buildPlayerHead(inv, player);
        buildRankItems(inv, player);
        buildInfoItem(inv);

        plugin.getSoundManager().playOpen(player);
        player.openInventory(inv);
        OPEN_VIEWERS.add(player.getUniqueId());
    }

    // ── Builder Helpers ───────────────────────────────────────────────────────

    private void buildBorder(Inventory inv) {
        for (int i = 0; i < 9; i++)   inv.setItem(i,  RankItemBuilder.glassPane(Material.CYAN_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) inv.setItem(i,  RankItemBuilder.glassPane(Material.CYAN_STAINED_GLASS_PANE));
    }

    private void buildPlayerHead(Inventory inv, Player player) {
        String cur     = getCurrentRankId(player);
        String nextId  = plugin.getRankManager().getNextRankId(cur);
        double pct     = plugin.getApi().getProgress(player);
        String curName = plugin.getRankManager().getDisplayName(cur);
        String nxtName = (nextId == null || nextId.isBlank()) ? "§6MAX RANK"
                : plugin.getRankManager().getDisplayName(nextId);
        double balance = plugin.getSoftDependency().getBalance(player);
        String bar     = plugin.getApi().getProgressService().getProgressBar(player);

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) { inv.setItem(4, skull); return; }

        meta.setOwningPlayer(player);
        meta.setDisplayName("§b§l" + player.getName());
        meta.setLore(List.of(
                "",
                "§7  Current Rank: " + curName,
                "§7  Next Rank:    " + nxtName,
                "",
                "§7  Money:    §a$" + String.format("%,.0f", balance),
                "§7  XP Level: §a" + player.getLevel(),
                "",
                "§7  Progress: " + bar + " §e" + String.format("%.1f", pct) + "§7%",
                ""
        ));
        skull.setItemMeta(meta);
        inv.setItem(4, skull);
    }

    private void buildRankItems(Inventory inv, Player player) {
        String currentId = getCurrentRankId(player);
        boolean passedCurrent = false;
        for (RankModel rank : plugin.getRankManager().getModelList()) {
            boolean isCurrent  = rank.getId().equals(currentId);
            boolean isUnlocked = !passedCurrent && !isCurrent;
            if (isCurrent) passedCurrent = true;
            int slot = rank.getSlot();
            if (slot >= 9 && slot <= 44) inv.setItem(slot, RankItemBuilder.build(rank, isCurrent, isUnlocked));
        }
    }

    private void buildInfoItem(Inventory inv) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lRankForge §7v" + plugin.getDescription().getVersion());
            meta.setLore(List.of("§7Author: §eJoshuaOP", "§7Click your next rank to rank up!"));
            item.setItemMeta(meta);
        }
        inv.setItem(49, item);
    }

    // ── Click Handling ────────────────────────────────────────────────────────

    public void handleClick(Player player, int rawSlot) {
        if (rawSlot < 9 || rawSlot > 44) return;
        String nextId = plugin.getRankManager().getNextRankId(getCurrentRankId(player));
        for (RankModel rank : plugin.getRankManager().getModelList()) {
            if (rank.getSlot() == rawSlot && rank.getId().equals(nextId)) {
                plugin.getApi().rankUp(player);
                return;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getCurrentRankId(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        return cache.contains(player.getUniqueId())
                ? cache.get(player.getUniqueId()).rankId()
                : plugin.getRankManager().getDefaultRankId();
    }

    public static boolean isOpen(UUID uuid)    { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid) { OPEN_VIEWERS.remove(uuid); }
}
