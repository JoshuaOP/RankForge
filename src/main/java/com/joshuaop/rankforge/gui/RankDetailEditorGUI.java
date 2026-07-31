package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.permission.PermissionRegistry;
import com.joshuaop.rankforge.rank.RankModel;
import com.joshuaop.rankforge.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Per-rank detail editor GUI.
 * Click any property item to edit its value via chat input.
 * All edits auto-save to ranks.yml instantly — no save button needed.
 *
 * Layout (54 slots):
 *   Row 1  (0–8)   — green border
 *   Row 2  (9–17)  — Display Name | Next Rank | Slot | Material
 *   Row 3  (18–26) — Money | XP Level | Permission | Chat Prefix
 *   Row 4  (27–35) — Playtime | Mob Kills | Block Breaks | Statistic
 *   Row 5  (36–44) — Quests | Worlds | Items | Lore
 *   Row 6  (45–53) — [Back] | Granted Perms | Commands | [Delete] | [Reload] | [Close]
 */
public class RankDetailEditorGUI {

    private static final Map<Integer, String[]> EDITABLE_FIELDS = new LinkedHashMap<>();
    static {
        // Row 2 — identity
        EDITABLE_FIELDS.put(10, new String[]{"displayName",      "§bEnter new display name §7(supports & color codes):"});
        EDITABLE_FIELDS.put(12, new String[]{"nextRankId",        "§bEnter next rank ID §7(or 'none' for MAX rank):"});
        EDITABLE_FIELDS.put(14, new String[]{"slot",              "§bEnter GUI slot number §7(9–44):"});
        EDITABLE_FIELDS.put(16, new String[]{"material",          "§bEnter material name §7(e.g. DIAMOND_BLOCK):"});
        // Row 3 — core requirements + display
        EDITABLE_FIELDS.put(19, new String[]{"money",             "§bEnter required money §7(e.g. 1000, or 0 for none):"});
        EDITABLE_FIELDS.put(21, new String[]{"xpLevel",           "§bEnter required XP level §7(e.g. 10, or 0 for none):"});
        EDITABLE_FIELDS.put(23, new String[]{"permission",        "§bEnter required permission §7(or 'none'):"});
        EDITABLE_FIELDS.put(25, new String[]{"chatPrefix",        "§bEnter chat prefix §7(supports & colors, or 'none'):"});
        // Row 4 — extended requirements
        EDITABLE_FIELDS.put(28, new String[]{"playTime",          "§bEnter required playtime §7(e.g. §e30m§7, §e2hr§7, §e1d 12hr§7, §e5d 5hr 5m§7, or §e0m §7for none):"});
        EDITABLE_FIELDS.put(30, new String[]{"mobKills",          "§bEnter required mob kills §7(e.g. 100, or 0 for none):"});
        EDITABLE_FIELDS.put(32, new String[]{"blockBreaks",       "§bEnter required block breaks §7(e.g. 500, or 0 for none):"});
        EDITABLE_FIELDS.put(34, new String[]{"statistic",
                "§bEnter statistic requirement §7(format: STAT_NAME:value, e.g. DEATHS:5, or 'none'):"});
        // Row 5 — list requirements + lore
        EDITABLE_FIELDS.put(37, new String[]{"quests",
                "§bEnter required quest IDs §7(comma-separated, e.g. quest1,quest2, or 'none'):"});
        EDITABLE_FIELDS.put(39, new String[]{"worlds",
                "§bEnter required worlds §7(comma-separated, e.g. world,world_nether, or 'none'):"});
        EDITABLE_FIELDS.put(41, new String[]{"items",
                "§bEnter required items §7(format: MATERIAL:amount,MAT2:amount, e.g. DIAMOND:5, or 'none'):"});
        EDITABLE_FIELDS.put(43, new String[]{"lore",
                "§bEnter rank description/lore §7(use §e|§7 to separate lines, & for colors, or 'none'):"});
    }

    // UUID → {rankId, fieldKey}
    private static final Map<UUID, String[]> PENDING_EDIT      = new HashMap<>();
    private static final Map<UUID, String>   EDITING_RANK      = new HashMap<>();
    private static final Set<UUID>           OPEN_VIEWERS      = new HashSet<>();
    private static final Map<UUID, Integer>  PENDING_CREATE    = new HashMap<>();
    private static final Map<UUID, String>   PENDING_CREATE_ID = new HashMap<>();
    private static final Map<UUID, String>   PENDING_DELETE    = new HashMap<>();

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

        String title = plugin.getGuiConfig().detailTitlePrefix() + "§e" + rank.getDisplayName() + " §8✦";
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
        ItemStack green = RankItemBuilder.glassPane(Material.LIME_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, green);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     green);
            inv.setItem(row * 9 + 8, green);
        }
    }

    private void buildProperties(Inventory inv, RankModel r) {
        // ── Row 2: Identity ───────────────────────────────────────────────────
        inv.setItem(10, prop(Material.BOOK, "§e§lDisplay Name",
                List.of("§f" + r.getDisplayName(), "", "§7ID: §e" + r.getId(), "", "§a▶ Click to edit")));
        inv.setItem(12, prop(Material.ARROW, "§b§lNext Rank",
                List.of("§f" + (r.getNextRankId().isBlank() ? "§cNone (MAX)" : r.getNextRankId()), "", "§a▶ Click to edit")));
        inv.setItem(14, prop(Material.COMPASS, "§d§lGUI Slot",
                List.of("§fSlot §e" + r.getSlot(), "§7(inventory slots 9-44)", "", "§a▶ Click to edit")));
        inv.setItem(16, prop(parseMaterial(r.getMaterial()), "§a§lDisplay Material",
                List.of("§f" + r.getMaterial(), "", "§a▶ Click to edit")));

        // ── Row 3: Core requirements + display ────────────────────────────────
        inv.setItem(19, prop(Material.GOLD_INGOT, "§6§lRequired Money",
                List.of("§f$" + String.format("%,.0f", r.getRequiredMoney()),
                        r.getRequiredMoney() <= 0 ? "§8Disabled" : "§aEnabled",
                        "", "§a▶ Click to edit")));
        inv.setItem(21, prop(Material.EXPERIENCE_BOTTLE, "§2§lRequired XP Level",
                List.of("§fLevel §a" + r.getRequiredXpLevel(),
                        r.getRequiredXpLevel() <= 0 ? "§8Disabled" : "§aEnabled",
                        "", "§a▶ Click to edit")));
        inv.setItem(23, prop(Material.PAPER, "§c§lRequired Permission",
                List.of("§f" + (r.getRequiredPermission().isBlank() ? "§8None" : r.getRequiredPermission()),
                        "", "§a▶ Click to edit")));
        inv.setItem(25, prop(Material.NAME_TAG, "§5§lChat Prefix",
                List.of("§f" + (r.getChatPrefix().isBlank() ? "§8None" : r.getChatPrefix()),
                        "", "§a▶ Click to edit")));

        // ── Row 4: Extended requirements ──────────────────────────────────────
        inv.setItem(28, prop(Material.CLOCK, "§b§lRequired Playtime",
                List.of("§f" + (r.getRequiredPlayTime() > 0 ? FormatUtil.formatTime(r.getRequiredPlayTime()) : "§8None"),
                        "", "§a▶ Click to edit §7(e.g. 30m, 2hr, 1d 12hr)")));
        inv.setItem(30, prop(Material.BONE, "§4§lRequired Mob Kills",
                List.of("§f" + (r.getRequiredMobKills() > 0 ? r.getRequiredMobKills() + " kills" : "§8None"),
                        "", "§a▶ Click to edit")));
        inv.setItem(32, prop(Material.IRON_PICKAXE, "§7§lRequired Block Breaks",
                List.of("§f" + (r.getRequiredBlockBreaks() > 0 ? r.getRequiredBlockBreaks() + " blocks" : "§8None"),
                        "", "§a▶ Click to edit")));
        {
            boolean hasStat = r.getRequiredStatisticId() != null && !r.getRequiredStatisticId().isBlank();
            inv.setItem(34, prop(Material.COMPARATOR, "§3§lCustom Statistic",
                    List.of(hasStat
                                    ? "§f" + r.getRequiredStatisticId() + " §8≥ §a" + r.getRequiredStatisticValue()
                                    : "§8None",
                            "", "§7Format: §eStat_NAME:value",
                            "§a▶ Click to edit")));
        }

        // ── Row 5: List requirements + lore ───────────────────────────────────
        {
            List<String> questLore = new ArrayList<>();
            questLore.add("§7Quest IDs (via permission check):");
            if (r.getRequiredQuests().isEmpty()) questLore.add("  §8None");
            else r.getRequiredQuests().forEach(q -> questLore.add("  §a" + q));
            questLore.add("");
            questLore.add("§a▶ Click to edit §7(comma-separated)");
            inv.setItem(37, prop(Material.WRITABLE_BOOK, "§e§lRequired Quests", questLore));
        }
        {
            List<String> worldLore = new ArrayList<>();
            worldLore.add("§7Player must be in one of:");
            if (r.getRequiredWorlds().isEmpty()) worldLore.add("  §8None (any world)");
            else r.getRequiredWorlds().forEach(w -> worldLore.add("  §a" + w));
            worldLore.add("");
            worldLore.add("§a▶ Click to edit §7(comma-separated)");
            inv.setItem(39, prop(Material.GRASS_BLOCK, "§9§lRequired Worlds", worldLore));
        }
        {
            List<String> itemLore = new ArrayList<>();
            itemLore.add("§7Items required in inventory:");
            if (r.getRequiredItems().isEmpty()) itemLore.add("  §8None");
            else r.getRequiredItems().forEach((mat, amt) -> itemLore.add("  §a" + amt + "x §f" + mat));
            itemLore.add("");
            itemLore.add("§a▶ Click to edit §7(MATERIAL:amount,...)");
            inv.setItem(41, prop(Material.CHEST, "§6§lRequired Items", itemLore));
        }
        {
            List<String> loreLore = new ArrayList<>();
            loreLore.add("§7Rank description shown in GUI:");
            if (r.getLore().isEmpty()) loreLore.add("  §8None");
            else r.getLore().forEach(l -> loreLore.add("  " + l));
            loreLore.add("");
            loreLore.add("§a▶ Click to edit §7(use | to separate lines)");
            inv.setItem(43, prop(Material.FILLED_MAP, "§f§lDescription / Lore", loreLore));
        }
    }

    /**
     * Build the Row 6 control bar with live rank data.
     * Layout: 45=Back | 47=GrantedPerms | 48=Commands | 49=Delete | 51=Reload | 53=Close
     */
    private void buildControls(Inventory inv, RankModel rank) {
        inv.setItem(45, makeBtn(Material.RED_WOOL, "§c§l← Back",
                List.of("§7Return to Admin Editor")));

        List<String> permLore = new ArrayList<>();
        permLore.add("§7Permissions granted on rank-up:");
        if (rank.getPermissions().isEmpty()) permLore.add("  §8None");
        else rank.getPermissions().forEach(p -> permLore.add("  §a" + p));
        permLore.add(""); permLore.add("§8Edit in ranks.yml and hot-reload.");
        inv.setItem(47, prop(Material.FILLED_MAP, "§3§lGranted Permissions", permLore));

        List<String> cmdLore = new ArrayList<>();
        cmdLore.add("§7Console commands on rank-up:");
        if (rank.getCommands().isEmpty()) cmdLore.add("  §8None");
        else rank.getCommands().forEach(c -> cmdLore.add("  §e" + c));
        cmdLore.add(""); cmdLore.add("§8Edit in ranks.yml and hot-reload.");
        inv.setItem(48, prop(Material.COMMAND_BLOCK, "§c§lOn-Rankup Commands", cmdLore));

        inv.setItem(49, makeBtn(Material.RED_DYE,    "§c§lDelete Rank",
                List.of("§7Permanently deletes this rank.", "§cThis cannot be undone!")));
        inv.setItem(51, makeBtn(Material.YELLOW_DYE, "§e§lHot-Reload",
                List.of("§7Reloads ranks.yml from disk.")));
        inv.setItem(53, makeBtn(Material.BARRIER,    "§c§lClose",
                List.of("§7Close this GUI")));
    }

    // ── Click Handling ────────────────────────────────────────────────────────

    public void handleClick(Player player, int slot) {
        switch (slot) {
            case 45 -> { player.closeInventory(); new AdminRankEditorGUI(plugin).open(player); return; }
            case 49 -> { handleDelete(player); return; }
            case 51 -> { handleReload(player); return; }
            case 53 -> { player.closeInventory(); return; }
        }

        String[] fieldInfo = EDITABLE_FIELDS.get(slot);
        if (fieldInfo == null) return;

        String rankId = EDITING_RANK.get(player.getUniqueId());
        if (rankId == null) return;

        player.closeInventory();
        PENDING_EDIT.put(player.getUniqueId(), new String[]{rankId, fieldInfo[0]});
        player.sendMessage("§8§m                                                  ");
        player.sendMessage(fieldInfo[1]);
        player.sendMessage("§7Type §ccancel §7to abort.");
        player.sendMessage("§8§m                                                  ");
    }

    // ── Chat Input Handling ───────────────────────────────────────────────────

    public static boolean hasPendingEdit(UUID uuid)   { return PENDING_EDIT.containsKey(uuid); }
    public static boolean hasPendingCreate(UUID uuid) { return PENDING_CREATE.containsKey(uuid); }
    public static boolean hasPendingDelete(UUID uuid) { return PENDING_DELETE.containsKey(uuid); }

    public static void setPendingCreate(UUID uuid) {
        PENDING_CREATE.put(uuid, 0);
        PENDING_CREATE_ID.remove(uuid);
    }

    public static void setPendingDelete(UUID uuid, String rankId) {
        PENDING_DELETE.put(uuid, rankId);
    }

    /** Apply the typed value to the rank field and auto-save. Called from GUIListener on main thread. */
    public void applyEdit(Player player, String input) {
        String[] edit = PENDING_EDIT.remove(player.getUniqueId());
        if (edit == null) return;

        String rankId   = edit[0];
        String fieldKey = edit[1];

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

        plugin.getRankYamlManager().updateRank(updated, true);
        plugin.getRankManager().updateModel(updated);
        player.sendMessage("§a✔ Updated §e" + fieldKey + " §afor §e" + rankId + " §8(auto-saved)");
        open(player, rankId);
    }

    /** Handle rank creation chat session. Called from GUIListener on main thread. */
    public void applyCreate(Player player, String input) {
        int step = PENDING_CREATE.getOrDefault(player.getUniqueId(), -1);
        if (step < 0) return;

        if (input.equalsIgnoreCase("cancel")) {
            PENDING_CREATE.remove(player.getUniqueId());
            PENDING_CREATE_ID.remove(player.getUniqueId());
            player.sendMessage("§cRank creation cancelled.");
            new AdminRankEditorGUI(plugin).open(player);
            return;
        }

        if (step == 0) {
            String rankId = input.trim().replaceAll("\\s+", "_");
            if (rankId.isEmpty()) {
                player.sendMessage("§cRank ID cannot be empty. Try again:");
                return;
            }
            if (plugin.getRankManager().getRank(rankId) != null) {
                player.sendMessage("§cA rank with ID §e" + rankId + " §calready exists. Enter a different ID:");
                return;
            }
            PENDING_CREATE_ID.put(player.getUniqueId(), rankId);
            PENDING_CREATE.put(player.getUniqueId(), 1);
            player.sendMessage("§8§m                                                  ");
            player.sendMessage("§bEnter the §edisplay name §bfor rank §e" + rankId + "§b:");
            player.sendMessage("§7Supports color codes with &  (e.g. §a&aBuilder§7)");
            player.sendMessage("§7Type §ccancel §7to abort.");
            player.sendMessage("§8§m                                                  ");

        } else if (step == 1) {
            String rankId      = PENDING_CREATE_ID.remove(player.getUniqueId());
            PENDING_CREATE.remove(player.getUniqueId());

            String displayName = input.replace("&", "§");
            int nextSlot       = findNextFreeSlot();

            RankModel newRank = new RankModel.Builder(rankId)
                    .displayName(displayName)
                    .nextRankId("")
                    .slot(nextSlot)
                    .material("GRAY_WOOL")
                    .build();

            plugin.getRankYamlManager().updateRank(newRank, true);
            plugin.getRankManager().updateModel(newRank);
            player.sendMessage("§a✔ Rank §e" + rankId + " §acreated and auto-saved! Opening editor…");
            open(player, rankId);
        }
    }

    /** Handle delete confirmation. Called from GUIListener on main thread. */
    public void applyDelete(Player player, String input) {
        String rankId = PENDING_DELETE.remove(player.getUniqueId());
        if (rankId == null) return;

        if (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("confirm")) {
            plugin.getRankYamlManager().deleteRank(rankId);
            plugin.getRankManager().removeModel(rankId);
            plugin.getRankManager().repairOrphanedRanks();
            player.sendMessage("§a✔ Rank §e" + rankId + " §adeleted and removed from ranks.yml.");
            new AdminRankEditorGUI(plugin).open(player);
        } else {
            player.sendMessage("§cDeletion cancelled.");
            open(player, rankId);
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission(PermissionRegistry.ADMIN_RELOAD)) {
            plugin.getLangManager().send(player, "no_permission"); return;
        }
        plugin.getRankYamlManager().hotReload();
        plugin.getRankManager().loadRanks();
        plugin.getRankManager().repairOrphanedRanks();
        player.sendMessage("§a✔ Ranks hot-reloaded!");
        String rankId = EDITING_RANK.get(player.getUniqueId());
        if (rankId != null) { player.closeInventory(); open(player, rankId); }
    }

    private void handleDelete(Player player) {
        if (!player.hasPermission(PermissionRegistry.ADMIN_DELETE)) {
            plugin.getLangManager().send(player, "no_permission"); return;
        }
        String rankId = EDITING_RANK.get(player.getUniqueId());
        if (rankId == null) return;

        player.closeInventory();
        PENDING_DELETE.put(player.getUniqueId(), rankId);
        player.sendMessage("§8§m                                                  ");
        player.sendMessage("§c§lDelete Rank: §e" + rankId);
        player.sendMessage("§7Are you sure? Type §ayes §7to confirm or anything else to cancel.");
        player.sendMessage("§c⚠ This cannot be undone!");
        player.sendMessage("§8§m                                                  ");
    }

    private RankModel applyField(RankModel rank, String field, String raw) {
        String val = raw.replace("&", "§");
        return switch (field) {
            case "displayName" -> rank.withDisplayName(val);
            case "nextRankId"  -> rank.withNextRankId(val.equalsIgnoreCase("none") ? "" : raw.trim());
            case "slot"        -> {
                int s;
                try { s = Integer.parseInt(raw.trim()); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Must be a number between 9–44"); }
                if (s < 9 || s > 44) throw new IllegalArgumentException("Slot must be 9–44");
                yield rank.withSlot(s);
            }
            case "material"    -> {
                try { org.bukkit.Material.valueOf(raw.trim().toUpperCase()); }
                catch (Exception e) { throw new IllegalArgumentException("Unknown material: " + raw.trim()); }
                yield rank.withMaterial(raw.trim().toUpperCase());
            }
            case "money"       -> {
                try { yield rank.withRequiredMoney(Double.parseDouble(raw.trim())); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Must be a number (e.g. 1000)"); }
            }
            case "xpLevel"     -> {
                try { yield rank.withRequiredXpLevel(Integer.parseInt(raw.trim())); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Must be an integer (e.g. 10)"); }
            }
            case "permission"  -> rank.withRequiredPermission(raw.equalsIgnoreCase("none") ? "" : raw.trim());
            case "chatPrefix"  -> rank.withChatPrefix(raw.equalsIgnoreCase("none") ? "" : val.trim());
            case "playTime" -> {
                long v = FormatUtil.parsePlaytimeString(raw.trim());
                yield rank.withRequiredPlayTime(v);
            }
            case "mobKills"    -> {
                try {
                    int v = Integer.parseInt(raw.trim());
                    if (v < 0) throw new IllegalArgumentException("Must be ≥ 0");
                    yield rank.withRequiredMobKills(v);
                } catch (NumberFormatException e) { throw new IllegalArgumentException("Must be an integer (e.g. 100)"); }
            }
            case "blockBreaks" -> {
                try {
                    int v = Integer.parseInt(raw.trim());
                    if (v < 0) throw new IllegalArgumentException("Must be ≥ 0");
                    yield rank.withRequiredBlockBreaks(v);
                } catch (NumberFormatException e) { throw new IllegalArgumentException("Must be an integer (e.g. 500)"); }
            }
            case "statistic"   -> {
                if (raw.equalsIgnoreCase("none")) yield rank.withRequiredStatistic("", 0);
                String[] parts = raw.split(":", 2);
                if (parts.length != 2)
                    throw new IllegalArgumentException("Format: STAT_NAME:value (e.g. DEATHS:5)");
                String statName = parts[0].trim().toUpperCase();
                int statVal;
                try { statVal = Integer.parseInt(parts[1].trim()); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Value must be an integer"); }
                try { org.bukkit.Statistic.valueOf(statName); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown statistic: " + statName); }
                yield rank.withRequiredStatistic(statName, statVal);
            }
            case "quests"      -> {
                if (raw.equalsIgnoreCase("none")) yield rank.withRequiredQuests(List.of());
                List<String> quests = Arrays.stream(raw.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                yield rank.withRequiredQuests(quests);
            }
            case "worlds"      -> {
                if (raw.equalsIgnoreCase("none")) yield rank.withRequiredWorlds(List.of());
                List<String> worlds = Arrays.stream(raw.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                yield rank.withRequiredWorlds(worlds);
            }
            case "items"       -> {
                if (raw.equalsIgnoreCase("none")) yield rank.withRequiredItems(Map.of());
                Map<String, Integer> items = new LinkedHashMap<>();
                for (String entry : raw.split(",")) {
                    String[] p = entry.trim().split(":", 2);
                    if (p.length != 2)
                        throw new IllegalArgumentException("Format: MATERIAL:amount (e.g. DIAMOND:5)");
                    String matName = p[0].trim().toUpperCase();
                    int amt;
                    try { amt = Integer.parseInt(p[1].trim()); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Amount must be integer for " + matName); }
                    try { org.bukkit.Material.valueOf(matName); }
                    catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown material: " + matName); }
                    items.put(matName, amt);
                }
                yield rank.withRequiredItems(items);
            }
            case "lore"        -> {
                if (raw.equalsIgnoreCase("none")) yield rank.withLore(List.of());
                List<String> lines = Arrays.stream(raw.split("\\|"))
                        .map(s -> s.trim().replace("&", "§"))
                        .collect(Collectors.toList());
                yield rank.withLore(lines);
            }
            default            -> throw new IllegalArgumentException("Unknown field: " + field);
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int findNextFreeSlot() {
        Set<Integer> used = new HashSet<>();
        for (RankModel r : plugin.getRankManager().getModelList()) used.add(r.getSlot());
        for (int s = 9; s <= 44; s++) {
            if (!used.contains(s)) return s;
        }
        return 9;
    }

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

    public static boolean isOpen(UUID uuid)         { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid)       { OPEN_VIEWERS.remove(uuid); EDITING_RANK.remove(uuid); }
    public static String  getEditingRank(UUID uuid)  { return EDITING_RANK.get(uuid); }
    public static boolean matchesTitle(String title) { return title != null && title.startsWith("§8✦ §6Editing: "); }
}
