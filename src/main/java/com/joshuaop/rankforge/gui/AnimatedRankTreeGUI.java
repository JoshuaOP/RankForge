package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.ProgressService;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Main player-facing rank tree GUI.
 *
 * Layout (54 slots):
 *   Slot 4   — Player head info panel
 *   Slots 9–44 — Rank items at their configured slots
 *   Slot 49  — Dynamic Next Rank display item
 *   Row 1 + Row 6 — Cyan glass border
 *
 * Improvements:
 *   - Requirements shown for ALL ranks using ProgressService (covers money, XP, playtime,
 *     mob kills, block breaks, items, permissions, quests, worlds, statistics, custom).
 *   - Next rank item uses buildNext() variant with emerald-block highlight when ready.
 *   - Player head shows a richer summary with progress bar and balance.
 *   - Status icons: ✔ completed, ⏳ current, ➤ next/in-progress, 🔒 locked.
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
        buildInfoItem(inv, player);

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
        String cur      = getCurrentRankId(player);
        String nextId   = plugin.getRankManager().getNextRankId(cur);
        double pct      = plugin.getApi().getProgress(player);
        String curName  = plugin.getRankManager().getDisplayName(cur);
        String nxtName  = (nextId == null || nextId.isBlank()) ? "§6§lMAX RANK"
                          : plugin.getRankManager().getDisplayName(nextId);
        double balance  = safeBalance(player);
        String bar      = plugin.getApi().getProgressService().getProgressBar(player);
        String pctColor = pct >= 100.0 ? "§a" : pct >= 50.0 ? "§e" : "§c";

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) { inv.setItem(4, skull); return; }

        meta.setOwningPlayer(player);
        meta.setDisplayName("§b§l" + player.getName());
        meta.setLore(List.of(
                "",
                "§7  Current Rank:  " + curName,
                "§7  Next Rank:     " + nxtName,
                "",
                "§7  Balance:   §a$" + String.format("%,.0f", balance),
                "§7  XP Level:  §a" + player.getLevel(),
                "",
                ""
        ));
        skull.setItemMeta(meta);
        inv.setItem(4, skull);
    }

    private void buildRankItems(Inventory inv, Player player) {
        String  currentId     = getCurrentRankId(player);
        String  nextId        = plugin.getRankManager().getNextRankId(currentId);
        boolean passedCurrent = false;

        for (RankModel rank : plugin.getRankManager().getModelList()) {
            boolean isCurrent = rank.getId().equals(currentId);
            boolean isNext    = rank.getId().equals(nextId);
            boolean isUnlocked = !passedCurrent && !isCurrent;
            if (isCurrent) passedCurrent = true;

            int slot = rank.getSlot();
            if (slot < 9 || slot > 44) continue;

            ItemStack item;
            if (isNext) {
                boolean meetsReqs = plugin.getRequirementManager().meetsAll(player, rank.getId());
                item = RankItemBuilder.buildNext(rank, meetsReqs);
                item = appendRequirementStatus(item, player, rank, meetsReqs);
            } else {
                item = RankItemBuilder.build(rank, isCurrent, isUnlocked);
            }

            inv.setItem(slot, item);
        }
    }

    /**
     * Appends live per-requirement progress to the next-rank item using ProgressService.
     * Covers all requirement types: money, XP, playtime, mob kills, block breaks,
     * items, permissions, quests, worlds, statistics, and custom requirements.
     */
    private ItemStack appendRequirementStatus(ItemStack item, Player player, RankModel rank, boolean meetsAll) {
        if (item == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        List<ProgressService.RequirementProgress> reqs =
                plugin.getApi().getProgressService().getRequirementProgress(player, rank);

        if (reqs.isEmpty()) {
            lore.add("");
            lore.add("§7No requirements to advance.");
        } else {
            lore.add("");
            lore.add("§8§m──────────────────────────");
            for (ProgressService.RequirementProgress rp : reqs) {
                lore.add(rp.toDisplayLine());
            }
        }

        lore.add("");
        lore.add(meetsAll
                ? "§a§l✔ All requirements met! §eClick to rank up."
                : "§c§l✘ Requirements not yet met.");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void buildInfoItem(Inventory inv, Player player) {
        String currentId = getCurrentRankId(player);
        String nextId    = plugin.getRankManager().getNextRankId(currentId);

        if (nextId == null || nextId.isBlank()) {
            ItemStack maxItem = new ItemStack(Material.NETHER_STAR);
            ItemMeta  maxMeta = maxItem.getItemMeta();
            if (maxMeta != null) {
                maxMeta.setDisplayName("§6§l✦ Maximum Rank Achieved ✦");
                maxMeta.setLore(List.of(
                        "",
                        "§7You have reached the highest rank",
                        "§7on the server. Congratulations!",
                        ""
                ));
                maxItem.setItemMeta(maxMeta);
            }
            inv.setItem(49, maxItem);
            return;
        }

        RankModel nextRank = plugin.getRankManager().getRank(nextId);
        if (nextRank == null) return;

        Material mat;
        try { mat = Material.valueOf(nextRank.getMaterial()); }
        catch (IllegalArgumentException e) { mat = Material.BOOK; }

        boolean canRankUp = plugin.getRequirementManager().meetsAll(player, nextRank.getId());
        double  pct       = plugin.getApi().getProgressService().getPercent(player);
        String  bar       = ProgressService.buildBar(pct, 20);

        ItemStack item = new ItemStack(canRankUp ? Material.EMERALD : mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l➤ Next: " + nextRank.getDisplayName());
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Overall progress:");
            lore.add("§8 [" + bar + "§8] §e" + String.format("%.1f", pct) + "§7%");
            lore.add("");
            lore.add(canRankUp
                    ? "§a§l✔ Click the rank above to advance!"
                    : "§c§l✘ Keep working toward the requirements.");
            meta.setLore(lore);
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
                boolean success = plugin.getApi().rankUp(player);
                if (success) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) open(player);
                    }, 1L);
                }
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

    private double safeBalance(Player player) {
        try {
            if (plugin.getSoftDependency() != null && plugin.getSoftDependency().hasVault()) {
                return plugin.getSoftDependency().getBalance(player);
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    public static boolean isOpen(UUID uuid)    { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid) { OPEN_VIEWERS.remove(uuid); }
}
