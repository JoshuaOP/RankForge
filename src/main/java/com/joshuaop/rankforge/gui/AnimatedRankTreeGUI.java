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
 * Slot 4   — Player head info panel
 * Slots 9-44 — Rank items at their configured slots
 * Slot 49  — Dynamic Next Rank display item
 * Row 1 + Row 6 — Cyan glass border
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
        String currentId      = getCurrentRankId(player);
        boolean passedCurrent = false;
        String nextId         = plugin.getRankManager().getNextRankId(currentId);

        for (RankModel rank : plugin.getRankManager().getModelList()) {
            boolean isCurrent  = rank.getId().equals(currentId);
            boolean isUnlocked = !passedCurrent && !isCurrent;
            boolean isNext     = rank.getId().equals(nextId);
            if (isCurrent) passedCurrent = true;

            int slot = rank.getSlot();
            if (slot < 9 || slot > 44) continue;

            ItemStack item = RankItemBuilder.build(rank, isCurrent, isUnlocked);

            if (isNext) {
                boolean meetsReqs = plugin.getRequirementManager().meetsAll(player, rank.getId());
                item = appendRequirementStatus(item, player, rank, meetsReqs);
            }

            inv.setItem(slot, item);
        }
    }

    private ItemStack appendRequirementStatus(ItemStack item, Player player, RankModel rank, boolean meetsAll) {
        if (item == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.getLore() != null ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
        lore.add("");
        lore.add("§8─── Requirements ───");

        if (rank.getRequiredMoney() > 0) {
            double bal = plugin.getSoftDependency().getBalance(player);
            boolean met = bal >= rank.getRequiredMoney();
            lore.add((met ? "§a✔" : "§c✘") + " §7Money: §e$" + String.format("%,.0f", rank.getRequiredMoney())
                    + (met ? "" : " §8(§chave $" + String.format("%,.0f", bal) + "§8)"));
        }
        if (rank.getRequiredXpLevel() > 0) {
            boolean met = player.getLevel() >= rank.getRequiredXpLevel();
            lore.add((met ? "§a✔" : "§c✘") + " §7XP Level: §eLevel " + rank.getRequiredXpLevel()
                    + (met ? "" : " §8(§chave " + player.getLevel() + "§8)"));
        }
        if (rank.getRequiredPermission() != null && !rank.getRequiredPermission().isBlank()) {
            boolean met = player.hasPermission(rank.getRequiredPermission());
            lore.add((met ? "§a✔" : "§c✘") + " §7Permission: §e" + rank.getRequiredPermission());
        }

        lore.add("");
        lore.add(meetsAll ? "§a✔ §lAll requirements met! §eClick to rank up." : "§c✘ §lRequirements not yet met.");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void buildInfoItem(Inventory inv, Player player) {
        String currentId = getCurrentRankId(player);
        String nextId    = plugin.getRankManager().getNextRankId(currentId);

        // Max rank state
        if (nextId == null || nextId.isBlank()) {
            ItemStack maxItem = new ItemStack(Material.BEDROCK);
            ItemMeta maxMeta = maxItem.getItemMeta();
            if (maxMeta != null) {
                maxMeta.setDisplayName("§6§lProgression");
                maxMeta.setLore(List.of("§7You have unlocked all available ranks!"));
                maxItem.setItemMeta(maxMeta);
            }
            inv.setItem(49, maxItem);
            return;
        }

        // Fetch the target Next Rank model directly by ID.
        RankModel nextRank = plugin.getRankManager().getRank(nextId);
        if (nextRank == null) return;

        Material mat;
        try {
            mat = Material.valueOf(nextRank.getMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.BOOK;
        }

        boolean canRankUp = plugin.getRequirementManager().meetsAll(player, nextRank.getId());

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lNext Rank: " + nextRank.getDisplayName());
            List<String> lore = new java.util.ArrayList<>();
            lore.add("");
            if (nextRank.getRequiredMoney() > 0)
                lore.add("§7Cost:      §a$" + String.format("%,.0f", nextRank.getRequiredMoney()));
            if (nextRank.getRequiredXpLevel() > 0)
                lore.add("§7XP Level:  §aLevel " + nextRank.getRequiredXpLevel());
            if (nextRank.getRequiredPermission() != null && !nextRank.getRequiredPermission().isBlank())
                lore.add("§7Perm:      §a" + nextRank.getRequiredPermission());
            lore.add("");
            lore.add(canRankUp
                    ? "§a✔ §lRequirements met — Click the rank to advance!"
                    : "§c✘ §7Requirements not yet met.");
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

    public static boolean isOpen(UUID uuid)    { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid) { OPEN_VIEWERS.remove(uuid); }
}
