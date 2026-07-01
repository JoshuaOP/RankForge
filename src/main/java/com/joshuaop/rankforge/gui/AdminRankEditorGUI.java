package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import com.joshuaop.rankforge.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Admin overview GUI — lists all loaded ranks and provides editor controls.
 * All rank changes auto-save; there is no manual save button.
 *
 * Layout (54 slots):
 *   Row 1  — red border
 *   Rows 2-5 — rank items (every other slot starting at 10)
 *   Row 6  — Create (45) | Reload (47) | Slot Editor (49) | Close (53)
 */
public class AdminRankEditorGUI {

    public static final String TITLE = "§8✦ §cAdmin Rank Editor §8✦";
    private static final Set<UUID> OPEN_VIEWERS = new HashSet<>();

    private final RankForge plugin;

    public AdminRankEditorGUI(RankForge plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        buildBorder(inv);
        buildRankList(inv);
        buildControls(inv);

        plugin.getSoundManager().playOpen(player);
        player.openInventory(inv);
        OPEN_VIEWERS.add(player.getUniqueId());
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void buildBorder(Inventory inv) {
        ItemStack pane = RankItemBuilder.glassPane(Material.RED_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
    }

    private void buildRankList(Inventory inv) {
        int slot = 10;
        for (RankModel rank : plugin.getRankManager().getModelList()) {
            if (slot > 43) break;
            inv.setItem(slot, buildRankCard(rank));
            slot += 2;
        }
    }

    private ItemStack buildRankCard(RankModel r) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§6§l✎ §e" + r.getDisplayName() + " §8[" + r.getId() + "]");

        List<String> lore = new ArrayList<>();
        lore.add("§8§m──────────────────────────");
        lore.add("§7Next Rank:    §e" + (r.getNextRankId().isBlank() ? "§c§lMAX" : r.getNextRankId()));
        lore.add("§7Slot:         §e" + r.getSlot() + "  §7Material: §e" + r.getMaterial());
        lore.add("§7Prefix:       §r" + (r.getChatPrefix().isBlank() ? "§8None" : r.getChatPrefix()));
        lore.add("§7Commands:     §e" + r.getCommands().size()
                + "  §7Permissions: §e" + r.getPermissions().size());

        boolean hasReqs = r.getRequiredMoney() > 0 || r.getRequiredXpLevel() > 0
                || (r.getRequiredPermission() != null && !r.getRequiredPermission().isBlank())
                || r.getRequiredPlayTime() > 0 || r.getRequiredMobKills() > 0
                || r.getRequiredBlockBreaks() > 0
                || (r.getRequiredStatisticId() != null && !r.getRequiredStatisticId().isBlank())
                || !r.getRequiredQuests().isEmpty() || !r.getRequiredWorlds().isEmpty()
                || !r.getRequiredItems().isEmpty();

        if (hasReqs) {
            lore.add("§8§m──────────────────────────");
            lore.add("§7§lRequirements:");
            if (r.getRequiredMoney() > 0)
                lore.add("  §8• §7Money:        §a$" + String.format("%,.0f", r.getRequiredMoney()));
            if (r.getRequiredXpLevel() > 0)
                lore.add("  §8• §7XP Level:     §aLevel " + r.getRequiredXpLevel());
            if (r.getRequiredPermission() != null && !r.getRequiredPermission().isBlank())
                lore.add("  §8• §7Permission:   §a" + r.getRequiredPermission());
            if (r.getRequiredPlayTime() > 0)
                lore.add("  §8• §7Playtime:     §a" + FormatUtil.formatTime(r.getRequiredPlayTime()));
            if (r.getRequiredMobKills() > 0)
                lore.add("  §8• §7Mob Kills:    §a" + String.format("%,d", r.getRequiredMobKills()));
            if (r.getRequiredBlockBreaks() > 0)
                lore.add("  §8• §7Block Breaks: §a" + String.format("%,d", r.getRequiredBlockBreaks()));
            if (r.getRequiredStatisticId() != null && !r.getRequiredStatisticId().isBlank())
                lore.add("  §8• §7Statistic:    §a" + r.getRequiredStatisticId()
                        + " ≥ " + String.format("%,d", r.getRequiredStatisticValue()));
            if (!r.getRequiredQuests().isEmpty())
                lore.add("  §8• §7Quests:       §a" + r.getRequiredQuests().size() + " required");
            if (!r.getRequiredWorlds().isEmpty())
                lore.add("  §8• §7Worlds:       §a" + String.join("§8, §a", r.getRequiredWorlds()));
            if (!r.getRequiredItems().isEmpty())
                lore.add("  §8• §7Items:        §a" + r.getRequiredItems().size() + " type(s)");
        } else {
            lore.add("§8§m──────────────────────────");
            lore.add("§7Requirements:  §8None");
        }

        lore.add("§8§m──────────────────────────");
        lore.add("§b§l» §bClick to open detail editor");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void buildControls(Inventory inv) {
        inv.setItem(45, makeBtn(Material.EMERALD,    "§a§l+ Create New Rank",
                new String[]{"§7Create a new rank.", "§7You will be prompted", "§7to enter the rank ID in chat."}));
        inv.setItem(47, makeBtn(Material.YELLOW_DYE, "§e§lHot-Reload",
                new String[]{"§7Reloads ranks.yml from disk.", "§7Unsaved auto-save changes will", "§7be replaced by the file on disk."}));
        inv.setItem(49, makeBtn(Material.CYAN_DYE,   "§b§lSlot Editor",
                new String[]{"§7Open the drag-drop slot editor."}));
        inv.setItem(53, makeBtn(Material.BARRIER,    "§c§lClose",
                new String[]{"§7Close this GUI."}));
    }

    private ItemStack makeBtn(Material mat, String name, String[] lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Click Handling ────────────────────────────────────────────────────────

    public void handleClick(Player player, int slot, String invTitle) {
        if (slot < 0) return;

        switch (slot) {
            case 45 -> handleCreateRank(player);
            case 47 -> handleReload(player);
            case 49 -> { player.closeInventory(); new DragDropRankEditorGUI(plugin).open(player); }
            case 53 -> player.closeInventory();
            default -> {
                Inventory inv = player.getOpenInventory().getTopInventory();
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType() != Material.BOOK) return;
                ItemMeta meta = item.getItemMeta();
                if (meta == null) return;
                String display = meta.getDisplayName();
                int idxOpen    = display.lastIndexOf('[');
                int idxClose   = display.lastIndexOf(']');
                if (idxOpen < 0 || idxClose <= idxOpen) return;
                String rankId  = display.substring(idxOpen + 1, idxClose);
                player.closeInventory();
                new RankDetailEditorGUI(plugin).open(player, rankId);
            }
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("rankforge.rank.reload")) {
            plugin.getLangManager().send(player, "no_permission"); return;
        }
        plugin.getRankYamlManager().hotReload();
        plugin.getRankManager().loadRanks();
        plugin.getRankManager().repairOrphanedRanks();
        player.sendMessage("§a✔ Ranks hot-reloaded from ranks.yml!");
        player.closeInventory();
        open(player);
    }

    private void handleCreateRank(Player player) {
        if (!player.hasPermission("rankforge.rank.create")) {
            plugin.getLangManager().send(player, "no_permission"); return;
        }
        player.closeInventory();
        RankDetailEditorGUI.setPendingCreate(player.getUniqueId());
        player.sendMessage("§8§m                                                  ");
        player.sendMessage("§b§lCreate New Rank");
        player.sendMessage("§7Type the §erank ID §7for the new rank.");
        player.sendMessage("§7Example: §eBuilder§7, §eVIP§7, §eAdmin");
        player.sendMessage("§7Type §ccancel §7to abort.");
        player.sendMessage("§8§m                                                  ");
    }

    public static boolean isOpen(UUID uuid)    { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid) { OPEN_VIEWERS.remove(uuid); }
}
