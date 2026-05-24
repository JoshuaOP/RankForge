package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.db.YamlPlayerDataStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI for editing a specific player's rank data.
 *
 * Layout (54 slots):
 *   Slot 4    — Target player head (read-only summary)
 *   Slot 10   — Rank
 *   Slot 12   — Experience
 *   Slot 14   — Money
 *   Slot 16   — Language
 *   Slot 45   — Back to Player List
 *   Slot 49   — Reset to default
 *   Slot 53   — Close
 */
public class PlayerDataEditorGUI {

    public static final String TITLE_PREFIX = "§8✦ §dEdit Player: §f";

    private static final Set<UUID>            OPEN_VIEWERS  = new HashSet<>();
    private static final Map<UUID, UUID>      EDITING_TARGET = new HashMap<>();
    private static final Map<UUID, String>    EDITING_NAME   = new HashMap<>();
    private static final Map<UUID, String[]>  PENDING_EDIT   = new HashMap<>();

    private final RankForge plugin;

    public PlayerDataEditorGUI(RankForge plugin) {
        this.plugin = plugin;
    }

    public void open(Player admin, UUID targetUuid, String targetName) {
        PlayerData data = loadTargetData(targetUuid, targetName);

        String title = TITLE_PREFIX + targetName;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        buildBorder(inv);
        buildPlayerHead(inv, targetUuid, targetName, data);
        buildFields(inv, data);
        buildControls(inv);

        plugin.getSoundManager().playOpen(admin);
        admin.openInventory(inv);
        OPEN_VIEWERS.add(admin.getUniqueId());
        EDITING_TARGET.put(admin.getUniqueId(), targetUuid);
        EDITING_NAME.put(admin.getUniqueId(), targetName);
    }

    private PlayerData loadTargetData(UUID uuid, String name) {
        var cache = plugin.getRankManager().getCacheManager();
        if (cache.contains(uuid)) return cache.get(uuid);

        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml != null && yaml.hasPlayer(uuid)) return yaml.loadPlayer(uuid, name);

        String defaultRank = plugin.getConfig().getString("ranks.default-rank", "Guest");
        return PlayerData.defaultData(uuid, name, defaultRank);
    }

    private void buildBorder(Inventory inv) {
        for (int i = 0; i < 9; i++)   inv.setItem(i,  RankItemBuilder.glassPane(Material.PURPLE_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) inv.setItem(i,  RankItemBuilder.glassPane(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void buildPlayerHead(Inventory inv, UUID uuid, String name, PlayerData data) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) { inv.setItem(4, skull); return; }

        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(op);
        meta.setDisplayName("§d§l" + name);
        meta.setLore(Arrays.asList(
                "",
                "§7UUID:       §8" + uuid,
                "§7Rank:       " + plugin.getRankManager().getDisplayName(data.rankId()),
                "§7Experience: §e" + data.experience(),
                "§7Money:      §a$" + String.format("%,.2f", data.money()),
                "§7Language:   §f" + data.language(),
                ""
        ));
        skull.setItemMeta(meta);
        inv.setItem(4, skull);
    }

    private void buildFields(Inventory inv, PlayerData data) {
        String rankDisplay = plugin.getRankManager().getDisplayName(data.rankId());
        inv.setItem(10, field(Material.PAPER,              "§e§lRank",
                data.rankId() + " §7(" + rankDisplay + ")", "§a▶ Click to change rank"));
        inv.setItem(12, field(Material.EXPERIENCE_BOTTLE, "§2§lExperience",
                String.valueOf(data.experience()), "§a▶ Click to set experience"));
        inv.setItem(14, field(Material.GOLD_INGOT,        "§6§lMoney",
                String.format("$%,.2f", data.money()), "§a▶ Click to set money"));
        inv.setItem(16, field(Material.BOOK,               "§b§lLanguage",
                data.language(), "§a▶ Click to change (en/es/fil/id)"));
    }

    private void buildControls(Inventory inv) {
        inv.setItem(45, makeBtn(Material.ARROW,   "§7§l← Back to List",  "§7Return to player list"));
        inv.setItem(49, makeBtn(Material.TNT,     "§c§lReset to Default", "§7Resets rank, experience,", "§7money and language to default."));
        inv.setItem(53, makeBtn(Material.BARRIER, "§c§lClose",            "§7Close this GUI"));
    }

    public void handleClick(Player admin, int slot) {
        UUID   targetUuid = EDITING_TARGET.get(admin.getUniqueId());
        String targetName = EDITING_NAME.get(admin.getUniqueId());
        if (targetUuid == null) { admin.closeInventory(); return; }

        switch (slot) {
            case 45 -> { admin.closeInventory(); new PlayerListGUI(plugin).open(admin); }
            case 53 -> admin.closeInventory();
            case 49 -> {
                resetPlayerData(admin, targetUuid, targetName);
            }
            case 10 -> startEdit(admin, targetUuid, "rank",
                    "§bEnter rank ID to assign §7(e.g. Member, Expert):", targetName);
            case 12 -> startEdit(admin, targetUuid, "experience",
                    "§bEnter experience value §7(e.g. 1000):", targetName);
            case 14 -> startEdit(admin, targetUuid, "money",
                    "§bEnter money amount §7(e.g. 5000.0):", targetName);
            case 16 -> startEdit(admin, targetUuid, "language",
                    "§bEnter language code §7(en, es, fil, id):", targetName);
            default -> {}
        }
    }

    private void startEdit(Player admin, UUID targetUuid, String field, String prompt, String targetName) {
        admin.closeInventory();
        PENDING_EDIT.put(admin.getUniqueId(), new String[]{targetUuid.toString(), field, targetName});
        admin.sendMessage("§8§m                                                  ");
        admin.sendMessage(prompt);
        admin.sendMessage("§7Type §ccancel §7to abort.");
        admin.sendMessage("§8§m                                                  ");
    }

    public void applyEdit(Player admin, String input) {
        String[] edit = PENDING_EDIT.remove(admin.getUniqueId());
        if (edit == null) return;

        UUID   targetUuid = UUID.fromString(edit[0]);
        String field      = edit[1];
        String targetName = edit[2];

        if (input.equalsIgnoreCase("cancel")) {
            admin.sendMessage("§cEdit cancelled.");
            open(admin, targetUuid, targetName);
            return;
        }

        PlayerData current = loadTargetData(targetUuid, targetName);
        PlayerData updated;
        try {
            updated = applyField(current, field, input.trim());
        } catch (IllegalArgumentException e) {
            admin.sendMessage("§c✘ Invalid value: §e" + e.getMessage());
            open(admin, targetUuid, targetName);
            return;
        }

        saveAndUpdate(admin, updated);
        admin.sendMessage("§a✔ Updated §e" + field + " §afor §e" + targetName + "§a!");
        open(admin, targetUuid, targetName);
    }

    private PlayerData applyField(PlayerData data, String field, String raw) {
        return switch (field) {
            case "rank" -> {
                if (!plugin.getRankManager().getRankIds().contains(raw))
                    throw new IllegalArgumentException("Unknown rank: " + raw);
                yield data.withRank(raw);
            }
            case "experience" -> {
                long val = Long.parseLong(raw);
                if (val < 0) throw new IllegalArgumentException("Experience cannot be negative.");
                yield data.withExperience(val);
            }
            case "money" -> {
                double val = Double.parseDouble(raw);
                if (val < 0) throw new IllegalArgumentException("Money cannot be negative.");
                yield data.withMoney(val);
            }
            case "language" -> {
                if (!plugin.getLangManager().isValidLang(raw))
                    throw new IllegalArgumentException("Unknown language: " + raw);
                yield data.withLanguage(raw);
            }
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        };
    }

    private void saveAndUpdate(Player admin, PlayerData data) {
        plugin.getRankManager().getCacheManager().put(data.uuid(), data);

        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml != null) yaml.savePlayer(data);

        if (plugin.getDatabaseManager().isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                    () -> plugin.getRankManager().getRepository().save(data));
        }

        Player online = Bukkit.getPlayer(data.uuid());
        if (online != null) {
            online.sendMessage("§6[RankForge] §7An admin has updated your player data.");
        }
    }

    private void resetPlayerData(Player admin, UUID targetUuid, String targetName) {
        String defaultRank = plugin.getConfig().getString("ranks.default-rank", "Guest");
        PlayerData reset   = PlayerData.defaultData(targetUuid, targetName, defaultRank);
        saveAndUpdate(admin, reset);
        admin.sendMessage("§a✔ Reset §e" + targetName + "§a's data to default!");
        open(admin, targetUuid, targetName);
    }

    private ItemStack field(Material mat, String name, String value, String hint) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList("§fValue: §e" + value, "", hint));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeBtn(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(s);
            meta.setLore(l);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean hasPendingEdit(UUID uuid)  { return PENDING_EDIT.containsKey(uuid); }
    public static boolean isOpen(UUID uuid)          { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid)       { OPEN_VIEWERS.remove(uuid); EDITING_TARGET.remove(uuid); EDITING_NAME.remove(uuid); }
    public static boolean matchesTitle(String title) { return title.startsWith(TITLE_PREFIX); }
}
