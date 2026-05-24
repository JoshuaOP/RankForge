package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Admin overview GUI — lists all loaded ranks and provides editor controls.
 *
 * Layout (54 slots):
 * Row 1  — red border
 * Rows 2-5 — rank items (slot 10, 12, 14 …)
 * Row 6  — controls: Save (47) | Reload (49) | Slot Editor (51)
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
        buildRankList(inv, player);
        buildControls(inv);

        plugin.getSoundManager().playOpen(player);
        player.openInventory(inv);
        OPEN_VIEWERS.add(player.getUniqueId());
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void buildBorder(Inventory inv) {
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,45,46,47,48,49,50,51,52,53})
            inv.setItem(i, RankItemBuilder.glassPane(Material.RED_STAINED_GLASS_PANE));
    }

    private void buildRankList(Inventory inv, Player player) {
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
        meta.setLore(Arrays.asList(
                "§7Next rank:     §e" + (r.getNextRankId().isBlank() ? "§cMAX" : r.getNextRankId()),
                "§7Slot:          §e" + r.getSlot(),
                "§7Material:      §e" + r.getMaterial(),
                "§7Money req:     §a$" + String.format("%,.0f", r.getRequiredMoney()),
                "§7XP req:        §a" + r.getRequiredXpLevel(),
                "§7Chat prefix:   §e" + (r.getChatPrefix().isBlank() ? "§8None" : r.getChatPrefix()),
                "§7Commands:      §e" + r.getCommands().size(),
                "§7Permissions:   §e" + r.getPermissions().size(),
                "",
                "§b» Click to open detail editor"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void buildControls(Inventory inv) {
        inv.setItem(47, makeBtn(Material.LIME_DYE,   "§a§lSave to ranks.yml",
                new String[]{"§7Saves all in-memory ranks", "§7to ranks.yml on disk."}));
        inv.setItem(49, makeBtn(Material.YELLOW_DYE, "§e§lHot-Reload",
                new String[]{"§7Reloads ranks.yml without restart."}));
        inv.setItem(51, makeBtn(Material.CYAN_DYE,   "§b§lSlot Editor",
                new String[]{"§7Open the drag-drop slot editor."}));
    }

    private ItemStack makeBtn(Material mat, String name, String[] lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta); }
        return item;
    }

    // ── Click Handling ────────────────────────────────────────────────────────

    public void handleClick(Player player, int slot, String invTitle) {
        if (slot < 0) return;

        switch (slot) {
            case 47 -> handleSave(player);
            case 49 -> handleReload(player, invTitle);
            case 51 -> {
                player.closeInventory();
                new DragDropRankEditorGUI(plugin).open(player);
            }
            default -> {
                Inventory inv = player.getOpenInventory().getTopInventory();
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType() != Material.BOOK) return;
                ItemMeta meta = item.getItemMeta();
                if (meta == null) return;
                String display = meta.getDisplayName();
                int idxOpen  = display.lastIndexOf('[');
                int idxClose = display.lastIndexOf(']');
                if (idxOpen < 0 || idxClose < 0) return;
                String rankId = display.substring(idxOpen + 1, idxClose);
                player.closeInventory();
                new RankDetailEditorGUI(plugin).open(player, rankId);
            }
        }
    }

    private void handleSave(Player player) {
        if (!player.hasPermission("rankforge.rank.editor.save")) {
            plugin.getLangManager().send(player, "no_permission"); return;
        }
        player.sendMessage("§7Saving ranks…");
        plugin.getRankYamlManager().saveAsync(() ->
                player.sendMessage("§a✔ Ranks saved to ranks.yml!"));
    }

    private void handleReload(Player player, String currentTitle) {
        if (!player.hasPermission("rankforge.rank.reload")) {
            plugin.getLangManager().send(player, "no_permission"); return;
        }
        plugin.getRankYamlManager().hotReload();
        plugin.getRankManager().loadRanks();
        player.sendMessage("§a✔ Ranks hot-reloaded from ranks.yml!");
        player.closeInventory();
        open(player);
    }

    public static boolean isOpen(UUID uuid)    { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid) { OPEN_VIEWERS.remove(uuid); }
}
