package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Per-rank detail editor GUI.
 * Click any property item to edit its value via chat input.
 *
 * Layout (54 slots):
 *   Row 1  — green border
 *   Row 2  — Display Name | Next Rank | Slot | Material
 *   Row 3  — Required Money | XP Level | Permission | Chat Prefix
 *   Row 4  — Granted Perms | On-Rankup Commands | Lore
 *   Row 5  — gray border
 *   Row 6  — [Back] ... [Save] ... [Reload] ... [Close]
 */
public class RankDetailEditorGUI {

    // slot → {fieldKey, prompt}
    private static final Map<Integer, String[]> EDITABLE_FIELDS = new LinkedHashMap<>();
    static {
        EDITABLE_FIELDS.put(10, new String[]{"displayName",  "§bEnter new display name §7(supports color codes with &):"});
        EDITABLE_FIELDS.put(12, new String[]{"nextRankId",   "§bEnter next rank ID §7(or 'none' for MAX rank):"});
        EDITABLE_FIELDS.put(14, new String[]{"slot",         "§bEnter GUI slot number §7(9–44):"});
        EDITABLE_FIELDS.put(16, new String[]{"material",     "§bEnter material name §7(e.g. DIAMOND_BLOCK):"});
        EDITABLE_FIELDS.put(19, new String[]{"money",        "§bEnter required money §7(e.g. 1000):"});
        EDITABLE_FIELDS.put(21, new String[]{"xpLevel",      "§bEnter required XP level §7(e.g. 10):"});
        EDITABLE_FIELDS.put(23, new String[]{"permission",   "§bEnter required permission §7(or 'none'):"});
        EDITABLE_FIELDS.put(25, new String[]{"chatPrefix",   "§bEnter chat prefix §7(supports color codes with &, or 'none'):"});
    }

    // UUID → {rankId, fieldKey}
    private static final Map<UUID, String[]> PENDING_EDIT = new HashMap<>();
    private static final Map<UUID, String>   EDITING_RANK = new HashMap<>();
    private static final Set<UUID>           OPEN_VIEWERS = new HashSet<>();

    private final RankForge plugin;

    public RankDetailEditorGUI(RankForge plugin) {
        this.plugin = plugin;
    }

    public boolean open(Player player, String rankId) {
        RankModel rank = plugin.getRankManager().getRank(rankId);
        if (rank == null) {
            player.sendMessage("§cRank not found: §e" + rankId);
            return false;
        }

        String title = "§8✦ §6Editing: §e" + rank.getDisplayName() + " §8✦";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        buildBorder(inv);
        buildProperties(inv, rank);
        buildControls(inv, rank);

        plugin.getSoundManager().playOpen(player);
        player.openInventory(inv);
        EDITING_RANK.put(player.getUniqueId(), rankId);
        OPEN_VIEWERS.add(player.getUniqueId());
        return true;
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void buildBorder(Inventory inv) {
        for (int i = 0; i < 9; i++)   inv.setItem(i,  RankItemBuilder.glassPane(Material.LIME_STAINED_GLASS_PANE));
        for (int i = 36; i < 54; i++) inv.setItem(i,  RankItemBuilder.glassPane(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void buildProperties(Inventory inv, RankModel r) {
        // Row 2 — identity (all clickable)
        inv.setItem(10, prop(Material.BOOK,              "§e§lDisplay Name",
                List.of("§f" + r.getDisplayName(), "", "§7ID: §e" + r.getId(), "", "§a▶ Click to edit")));
        inv.setItem(12, prop(Material.ARROW,             "§b§lNext Rank",
                List.of("§f" + (r.getNextRankId().isBlank() ? "§cNone (MAX)" : r.getNextRankId()), "", "§a▶ Click to edit")));
        inv.setItem(14, prop(Material.COMPASS,           "§d§lGUI Slot",
                List.of("§fSlot §e" + r.getSlot(), "§7(inventory slot 9-44)", "", "§a▶ Click to edit")));
        inv.setItem(16, prop(parseMaterial(r.getMaterial()), "§a§lDisplay Material",
                List.of("§f" + r.getMaterial(), "", "§a▶ Click to edit")));

        // Row 3 — requirements (all clickable)
        inv.setItem(19, prop(Material.GOLD_INGOT,        "§6§lRequired Money",
                List.of("§f$" + String.format("%,.0f", r.getRequiredMoney()), "", "§a▶ Click to edit")));
        inv.setItem(21, prop(Material.EXPERIENCE_BOTTLE, "§2§lRequired XP Level",
                List.of("§fLevel §a" + r.getRequiredXpLevel(), "", "§a▶ Click to edit")));
        inv.setItem(23, prop(Material.PAPER,             "§c§lRequired Permission",
                List.of("§f" + (r.getRequiredPermission().isBlank() ? "§8None" : r.getRequiredPermission()), "", "§a▶ Click to edit")));
        inv.setItem(25, prop(Material.NAME_TAG,          "§5§lChat Prefix",
                List.of("§f" + (r.getChatPrefix().isBlank() ? "§8None" : r.getChatPrefix()), "", "§a▶ Click to edit")));

        // Row 4 — read-only list fields
        List<String> permLore = new ArrayList<>();
        permLore.add("§7Permissions granted on rank-up:");
        if (r.getPermissions().isEmpty()) permLore.add("  §8None");
        else r.getPermissions().forEach(p -> permLore.add("  §a" + p));
        permLore.add(""); permLore.add("§7Edit in ranks.yml and hot-reload.");
        inv.setItem(28, prop(Material.FILLED_MAP, "§3§lGranted Permissions", permLore));

        List<String> cmdLore = new ArrayList<>();
        cmdLore.add("§7Console commands on rank-up:");
        if (r.getCommands().isEmpty()) cmdLore.add("  §8None");
        else r.getCommands().forEach(c -> cmdLore.add("  §e" + c));
        cmdLore.add(""); cmdLore.add("§7Edit in ranks.yml and hot-reload.");
        inv.setItem(31, prop(Material.COMMAND_BLOCK, "§c§lOn-Rankup Commands", cmdLore));

        List<String> loreLore = new ArrayList<>();
        loreLore.add("§7Item lore shown in GUI:");
        r.getLore().forEach(l -> loreLore.add("  " + l));
        loreLore.add(""); loreLore.add("§7Edit in ranks.yml and hot-reload.");
        inv.setItem(34, prop(Material.WRITABLE_BOOK, "§f§lLore", loreLore));
    }

    private void buildControls(Inventory inv, RankModel r) {
        inv.setItem(45, makeBtn(Material.RED_WOOL,   "§c§l← Back",         List.of("§7Return to Admin Editor")));
        inv.setItem(49, makeBtn(Material.LIME_DYE,   "§a§lSave ranks.yml", List.of("§7Saves all in-memory ranks to disk.")));
        inv.setItem(51, makeBtn(Material.YELLOW_DYE, "§e§lHot-Reload",     List.of("§7Reloads ranks.yml from disk.")));
        inv.setItem(53, makeBtn(Material.BARRIER,    "§c§lClose",          List.of("§7Close this GUI")));
    }

    // ── Click Handling ────────────────────────────────────────────────────────

    public void handleClick(Player player, int slot) {
        // Control buttons
        switch (slot) {
            case 45 -> { player.closeInventory(); new AdminRankEditorGUI(plugin).open(player); return; }
            case 49 -> { handleSave(player); return; }
            case 51 -> { handleReload(player); return; }
            case 53 -> { player.closeInventory(); return; }
        }

        // Property slots — open chat edit mode
        String[] fieldInfo = EDITABLE_FIELDS.get(slot);
        if (fieldInfo == null) return;

        String rankId = EDITING_RANK.get(player.getUniqueId());
        if (rankId == null) return;

        // Close GUI and prompt for input
        player.closeInventory();
        PENDING_EDIT.put(player.getUniqueId(), new String[]{rankId, fieldInfo[0]});
        player.sendMessage("§8§m                                                  ");
        player.sendMessage(fieldInfo[1]);
        player.sendMessage("§7Type §ccancel §7to abort.");
        player.sendMessage("§8§m                                                  ");
    }

    /**
     * Called by GUIListener when the player sends a chat message while in edit mode.
     * @return true if the edit was handled (cancel the chat event)
     */
    public static boolean hasPendingEdit(UUID uuid) {
        return PENDING_EDIT.containsKey(uuid);
    }

    /** Apply the typed value to the rank field. Called from GUIListener on main thread. */
    public void applyEdit(Player player, String input) {
        String[] edit = PENDING_EDIT.remove(player.getUniqueId());
        if (edit == null) return;

        String rankId    = edit[0];
        String fieldKey  = edit[1];

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage("§cEdit cancelled.");
            open(player, rankId);
            return;
        }

        RankModel rank = plugin.getRankManager().getRank(rankId);
        if (rank == null) { player.sendMessage("§cRank no longer exists."); return; }

        RankModel updated;
        try {
            updated = applyField(rank, fieldKey, input);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c✘ Invalid value: §e" + e.getMessage());
            open(player, rankId);
            return;
        }

        plugin.getRankYamlManager().updateRank(updated, false);
        plugin.getRankManager().updateModel(updated);
        player.sendMessage("§a✔ Updated §e" + fieldKey + " §afor §e" + rankId + "§a!");
        open(player, rankId);
    }

    private RankModel applyField(RankModel rank, String field, String raw) {
        String val = raw.replace("&", "§");
        return switch (field) {
            case "displayName" -> rank.withDisplayName(val);
            case "nextRankId"  -> rank.withNextRankId(val.equalsIgnoreCase("none") ? "" : raw.trim());
            case "slot"        -> {
                int s = Integer.parseInt(raw.trim());
                if (s < 9 || s > 44) throw new IllegalArgumentException("Slot must be 9–44");
                yield rank.withSlot(s);
            }
            case "material"    -> {
                try { org.bukkit.Material.valueOf(raw.trim().toUpperCase()); }
                catch (Exception e) { throw new IllegalArgumentException("Unknown material: " + raw); }
                yield rank.withMaterial(raw.trim().toUpperCase());
            }
            case "money"       -> rank.withRequiredMoney(Double.parseDouble(raw.trim()));
            case "xpLevel"     -> rank.withRequiredXpLevel(Integer.parseInt(raw.trim()));
            case "permission"  -> rank.withRequiredPermission(raw.equalsIgnoreCase("none") ? "" : raw.trim());
            case "chatPrefix"  -> rank.withChatPrefix(raw.equalsIgnoreCase("none") ? "" : val.trim());
            default            -> throw new IllegalArgumentException("Unknown field: " + field);
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack prop(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makeBtn(Material mat, String name, List<String> lore) {
        return prop(mat, name, lore);
    }

    private Material parseMaterial(String name) {
        try { return Material.valueOf(name); }
        catch (IllegalArgumentException e) { return Material.GRAY_WOOL; }
    }

    private void handleSave(Player player) {
        if (!player.hasPermission("rankforge.rank.editor.save")) {
            plugin.getLangManager().send(player, "no_permission"); return;
        }
        player.sendMessage("§7Saving ranks…");
        plugin.getRankYamlManager().saveAsync(() -> player.sendMessage("§a✔ Saved to ranks.yml!"));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("rankforge.rank.reload")) {
            plugin.getLangManager().send(player, "no_permission"); return;
        }
        plugin.getRankYamlManager().hotReload();
        plugin.getRankManager().loadRanks();
        player.sendMessage("§a✔ Ranks hot-reloaded!");
        String rankId = EDITING_RANK.get(player.getUniqueId());
        if (rankId != null) { player.closeInventory(); open(player, rankId); }
    }

    public static boolean isOpen(UUID uuid)    { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid) { OPEN_VIEWERS.remove(uuid); EDITING_RANK.remove(uuid); }
    public static String  getEditingRank(UUID uuid) { return EDITING_RANK.get(uuid); }
    public static boolean matchesTitle(String title) { return title.startsWith("§8✦ §6Editing: "); }
}
