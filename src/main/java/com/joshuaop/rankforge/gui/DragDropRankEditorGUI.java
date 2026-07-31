package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drag-and-drop slot reassignment editor.
 * Click a rank item to select it → click an empty slot to move it there.
 * Slot changes auto-save on every move; no manual save step required.
 *
 * Layout (54 slots):
 *   Row 1  — cyan border + info compass (slot 4)
 *   Rows 2-5 — rank items at their configured slots (9–44)
 *   Row 6  — [Back (45)] ... [Close (53)]
 */
public class DragDropRankEditorGUI {

    public static final String TITLE = "§8✦ §bSlot Editor §8✦";

    private static final Set<UUID>            OPEN_VIEWERS  = new HashSet<>();
    private static final Map<UUID, String>    SELECTED_RANK = new HashMap<>();
    private static final Map<UUID, Inventory> OPEN_INV      = new HashMap<>();

    private final RankForge plugin;

    public DragDropRankEditorGUI(RankForge plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.getGuiConfig().dragDropTitle());
        renderAll(inv);

        plugin.getSoundManager().playOpen(player);
        player.openInventory(inv);
        OPEN_VIEWERS.add(player.getUniqueId());
        OPEN_INV.put(player.getUniqueId(), inv);
    }

    private void renderAll(Inventory inv) {
        for (int i = 0; i < 9; i++)   inv.setItem(i,  RankItemBuilder.glassPane(Material.CYAN_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) inv.setItem(i,  RankItemBuilder.glassPane(Material.CYAN_STAINED_GLASS_PANE));

        inv.setItem(4, buildInfoItem());

        for (RankModel rank : plugin.getRankManager().getModelList()) {
            int slot = rank.getSlot();
            if (slot >= 9 && slot <= 44) inv.setItem(slot, buildRankItem(rank, false));
        }

        inv.setItem(45, makeBtn(Material.RED_WOOL, "§c§l← Back",  "§7Return to Admin Editor"));
        inv.setItem(53, makeBtn(Material.BARRIER,  "§c§lClose",   "§7Close this GUI"));
    }

    private ItemStack buildInfoItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lSlot Editor");
            meta.setLore(List.of(
                    "§71. §eClick a rank §7to select it.",
                    "§72. §eClick an empty slot §7to move it.",
                    "§7Slots §asave automatically §7when moved."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildRankItem(RankModel rank, boolean selected) {
        ItemStack item = new ItemStack(selected ? Material.GLOWSTONE : Material.PAPER);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((selected ? "§b⟩ " : "§e") + rank.getDisplayName());
            meta.setLore(List.of(
                    "§7ID:   §e" + rank.getId(),
                    "§7Slot: §e" + rank.getSlot(),
                    "§7Next: §e" + (rank.getNextRankId().isBlank() ? "§cNone" : rank.getNextRankId()),
                    "",
                    selected ? "§bClick a slot to move here" : "§eClick to select"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeBtn(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(List.of(lore)); item.setItemMeta(meta); }
        return item;
    }

    // ── Click Handling ────────────────────────────────────────────────────────

    public void handleClick(Player player, int rawSlot) {
        Inventory inv = OPEN_INV.get(player.getUniqueId());
        if (inv == null) return;

        if (rawSlot == 45) { handleBack(player);      return; }
        if (rawSlot == 53) { player.closeInventory(); return; }
        if (rawSlot < 9 || rawSlot > 44)              return;

        String selected = SELECTED_RANK.get(player.getUniqueId());
        if (selected == null) selectRank(player, inv, rawSlot);
        else                  moveRank(player, inv, selected, rawSlot);
    }

    private void selectRank(Player player, Inventory inv, int slot) {
        RankModel rank = plugin.getRankManager().getRankAtSlot(slot);
        if (rank == null) { player.sendMessage("§cNo rank at that slot."); return; }
        SELECTED_RANK.put(player.getUniqueId(), rank.getId());
        inv.setItem(slot, buildRankItem(rank, true));
        player.sendMessage("§bSelected §e" + rank.getDisplayName() + " §b— click a slot to move it.");
        plugin.getSoundManager().playClick(player);
    }

    private void moveRank(Player player, Inventory inv, String rankId, int newSlot) {
        RankModel existing = plugin.getRankManager().getRankAtSlot(newSlot);
        if (existing != null && !existing.getId().equals(rankId)) {
            player.sendMessage("§cSlot §e" + newSlot + " §cis occupied by §e" + existing.getDisplayName() + "§c!");
            return;
        }
        RankModel rank = plugin.getRankManager().getRank(rankId);
        if (rank == null) { SELECTED_RANK.remove(player.getUniqueId()); return; }

        int oldSlot    = rank.getSlot();
        RankModel updated = rank.withSlot(newSlot);

        plugin.getRankYamlManager().updateRank(updated, true);
        plugin.getRankManager().updateModel(updated);

        inv.setItem(oldSlot, null);
        inv.setItem(newSlot, buildRankItem(updated, false));
        SELECTED_RANK.remove(player.getUniqueId());
        player.sendMessage("§aMoved §e" + rank.getDisplayName() + " §a→ slot §e" + newSlot + " §8(auto-saved)");
        plugin.getSoundManager().playClick(player);
    }

    private void handleBack(Player player) {
        player.closeInventory();
        new AdminRankEditorGUI(plugin).open(player);
    }

    public static boolean isOpen(UUID uuid)    { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid) {
        OPEN_VIEWERS.remove(uuid);
        SELECTED_RANK.remove(uuid);
        OPEN_INV.remove(uuid);
    }
}
