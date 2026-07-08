package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
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
 * GUI for editing a specific player's rank data — works for both online and
 * offline players.
 *
 * Layout (54 slots):
 *   Slot 4  — Target player head (summary)
 *   Slot 10 — Rank
 *   Slot 12 — Experience
 *   Slot 14 — Economy Balance (Vault-synced for online; stored value for offline)
 *   Slot 16 — Rank History
 *   Slot 45 — Back to Player List
 *   Slot 49 — Reset to default
 *   Slot 53 — Close
 *
 * Offline player support:
 *   - Data is loaded from cache → MySQL → YAML in that priority order.
 *   - Edits are persisted to storage and reflected in cache regardless of online state.
 *   - Vault balance is updated via OfflinePlayer when the player is not online.
 *   - Online notification is sent if the player joins between an edit start and save.
 *
 * Null safety:
 *   - Every ItemMeta access is null-checked before use.
 *   - Missing player data falls back to defaults — the GUI never crashes on bad data.
 *   - Corrupted UUID strings in PENDING_EDIT are caught and discarded.
 */
public class PlayerDataEditorGUI {

    public static final String TITLE_PREFIX = "§8✦ §dEdit Player: §f";

    private static final Set<UUID>           OPEN_VIEWERS   = new HashSet<>();
    private static final Map<UUID, UUID>     EDITING_TARGET = new HashMap<>();
    private static final Map<UUID, String>   EDITING_NAME   = new HashMap<>();
    private static final Map<UUID, String[]> PENDING_EDIT   = new HashMap<>();

    private final RankForge plugin;

    public PlayerDataEditorGUI(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void open(Player admin, UUID targetUuid, String targetName) {
        if (admin == null || !admin.isOnline()) return;
        if (targetUuid == null) {
            admin.sendMessage("§c[RankForge] Cannot open editor: invalid player UUID.");
            return;
        }

        String safeName = (targetName != null && !targetName.isBlank()) ? targetName : "Unknown";
        PlayerData data = loadTargetData(targetUuid, safeName);

        if (data == null) {
            admin.sendMessage("§c[RankForge] Could not load data for §e" + safeName + "§c.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + safeName);
        buildBorder(inv);
        buildPlayerHead(inv, targetUuid, safeName, data);
        buildFields(inv, data);
        buildControls(inv);

        plugin.getSoundManager().playOpen(admin);
        admin.openInventory(inv);

        UUID adminUuid = admin.getUniqueId();
        OPEN_VIEWERS.add(adminUuid);
        EDITING_TARGET.put(adminUuid, targetUuid);
        EDITING_NAME.put(adminUuid, safeName);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Loads player data with live-stitch for online players.
     * Returns default data if nothing is found in cache or storage.
     */
    private PlayerData loadTargetData(UUID uuid, String name) {
        try {
            var cache = plugin.getRankManager().getCacheManager();
            PlayerData data = cache.get(uuid);
            if (data == null) data = plugin.getRankManager().getRepository().load(uuid, name);

            if (data == null) {
                String defaultRank = plugin.getConfig().getString("ranks.default-rank", "Guest");
                data = PlayerData.defaultData(uuid, name, defaultRank);
            }

            // Stitch live values for online players
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                if (plugin.getExperienceManager() != null) {
                    data = data.withExperience(plugin.getExperienceManager().getXp(online));
                }
                if (plugin.getSoftDependency().hasVault()) {
                    try { data = data.withMoney(plugin.getSoftDependency().getBalance(online)); }
                    catch (Exception ignored) {}
                }
            }
            return data;
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("loadTargetData failed for "
                        + uuid + ": " + e.getMessage());
            }
            String defaultRank = plugin.getConfig().getString("ranks.default-rank", "Guest");
            return PlayerData.defaultData(uuid, name, defaultRank);
        }
    }

    // ── GUI building ──────────────────────────────────────────────────────────

    private void buildBorder(Inventory inv) {
        ItemStack top    = makePane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack bottom = makePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0;  i < 9;  i++) inv.setItem(i,  top);
        for (int i = 45; i < 54; i++) inv.setItem(i,  bottom);
    }

    private void buildPlayerHead(Inventory inv, UUID uuid, String name, PlayerData data) {
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta  = (SkullMeta) skull.getItemMeta();
            if (meta == null) { inv.setItem(4, skull); return; }

            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
            meta.setDisplayName("§d§l" + name);

            boolean hasVault = plugin.getSoftDependency().hasVault();
            String moneyLabel = hasVault
                    ? "§7Balance: §a$" + String.format("%,.2f", data.money()) + " §8(Vault)"
                    : "§7Balance: §a$" + String.format("%,.2f", data.money());

            boolean isOnline = Bukkit.getPlayer(uuid) != null;
            meta.setLore(Arrays.asList(
                    "",
                    "§7UUID:       §8" + uuid,
                    "§7Rank:       " + safeDisplayName(data.rankId()),
                    "§7Experience: §e" + data.experience(),
                    moneyLabel,
                    "§7Language:   §f" + data.language(),
                    "§7Status:     " + (isOnline ? "§aOnline" : "§7Offline"),
                    ""
            ));
            skull.setItemMeta(meta);
            inv.setItem(4, skull);
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("buildPlayerHead failed for "
                        + uuid + ": " + e.getMessage());
            }
        }
    }

    private void buildFields(Inventory inv, PlayerData data) {
        String rankDisplay = safeDisplayName(data.rankId());
        boolean hasVault   = plugin.getSoftDependency().hasVault();
        String moneyHint = hasVault
                ? "§a▶ Click to set §8(updates Vault)"
                : "§a▶ Click to set §7(stored value only — no Vault)";

        inv.setItem(10, field(Material.PAPER, "§e§lRank",
                data.rankId() + " §7(" + rankDisplay + ")", "§a▶ Click to change rank"));
        inv.setItem(12, field(Material.EXPERIENCE_BOTTLE, "§2§lExperience",
                String.valueOf(data.experience()), "§a▶ Click to set experience"));
        inv.setItem(14, field(Material.GOLD_INGOT, "§6§lBalance",
                String.format("$%,.2f", data.money()), moneyHint));
        inv.setItem(16, makeBtn(Material.WRITTEN_BOOK, "§6§lRank History",
                "§7Click to view this player's rank history",
                "§a▶ Displays last 10 changes in chat"));
    }

    private void buildControls(Inventory inv) {
        inv.setItem(45, makeBtn(Material.ARROW,   "§7§l← Back to List",   "§7Return to player list"));
        inv.setItem(49, makeBtn(Material.TNT,     "§c§lReset to Default",
                "§7Resets rank, experience, money, and language to defaults.",
                "§c⚠ This cannot be undone!"));
        inv.setItem(53, makeBtn(Material.BARRIER, "§c§lClose",            "§7Close this GUI"));
    }

    // ── Click handling ────────────────────────────────────────────────────────

    public void handleClick(Player admin, int slot) {
        if (admin == null || !admin.isOnline()) return;
        UUID   targetUuid = EDITING_TARGET.get(admin.getUniqueId());
        String targetName = EDITING_NAME.get(admin.getUniqueId());
        if (targetUuid == null) { admin.closeInventory(); return; }

        switch (slot) {
            case 45 -> { admin.closeInventory(); new PlayerListGUI(plugin).open(admin); }
            case 53 -> admin.closeInventory();
            case 49 -> resetPlayerData(admin, targetUuid, targetName);
            case 10 -> startEdit(admin, targetUuid, "rank",
                    "§bEnter rank ID §7(e.g. Member, Expert):", targetName);
            case 12 -> startEdit(admin, targetUuid, "experience",
                    "§bEnter experience §7(e.g. 1000):", targetName);
            case 14 -> startEdit(admin, targetUuid, "money",
                    "§bEnter balance §7(e.g. 5000.0):", targetName);
            case 16 -> showHistoryForTarget(admin, targetUuid, targetName);
            default -> {}
        }
    }

    private void showHistoryForTarget(Player admin, UUID targetUuid, String targetName) {
        if (plugin.getHistoryManager() == null) {
            admin.sendMessage("§c[RankForge] History system not initialised.");
            return;
        }
        if (targetUuid == null) {
            admin.sendMessage("§c[RankForge] Cannot load history: invalid player UUID.");
            return;
        }

        admin.sendMessage("§6[RankForge] §7Loading rank history for §e" + targetName + "§7…");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<com.joshuaop.rankforge.experience.RankHistoryEntry> history =
                    plugin.getHistoryManager().getHistory(targetUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!admin.isOnline()) return;
                admin.sendMessage("§8§m                              ");
                admin.sendMessage("  §6Rank History §7for §e" + targetName
                        + " §8(" + history.size() + " entries)");
                admin.sendMessage("§8§m                              ");
                if (history.isEmpty()) {
                    admin.sendMessage("  §7No rank changes recorded yet.");
                } else {
                    int limit = Math.min(history.size(), 10);
                    for (int i = 0; i < limit; i++) {
                        admin.sendMessage("  " + history.get(i).toDisplayLine());
                    }
                    if (history.size() > 10) {
                        admin.sendMessage("  §8… and " + (history.size() - 10) + " more entries.");
                    }
                }
                admin.sendMessage("§8§m                              ");
            });
        });
    }

    private void startEdit(Player admin, UUID targetUuid, String field,
                            String prompt, String targetName) {
        admin.closeInventory();
        PENDING_EDIT.put(admin.getUniqueId(),
                new String[]{targetUuid.toString(), field, targetName});
        admin.sendMessage("§8§m                                                  ");
        admin.sendMessage(prompt);
        admin.sendMessage("§7Type §ccancel §7to abort.");
        admin.sendMessage("§8§m                                                  ");
    }

    // ── Edit application ──────────────────────────────────────────────────────

    public void applyEdit(Player admin, String input) {
        String[] edit = PENDING_EDIT.remove(admin.getUniqueId());
        if (edit == null || edit.length < 3) return;

        UUID   targetUuid;
        try {
            targetUuid = UUID.fromString(edit[0]);
        } catch (IllegalArgumentException e) {
            admin.sendMessage("§c[RankForge] Corrupted edit session — please try again.");
            return;
        }

        String field      = edit[1];
        String targetName = edit[2];

        if ("cancel".equalsIgnoreCase(input.trim())) {
            admin.sendMessage("§cEdit cancelled.");
            open(admin, targetUuid, targetName);
            return;
        }

        PlayerData current = loadTargetData(targetUuid, targetName);
        if (current == null) {
            admin.sendMessage("§c[RankForge] Could not reload data for §e" + targetName + "§c.");
            open(admin, targetUuid, targetName);
            return;
        }

        PlayerData updated;
        try {
            updated = applyField(current, field, input.trim());
        } catch (NumberFormatException e) {
            admin.sendMessage("§c✘ Invalid number format: §e" + input.trim()
                    + " §c— must be a valid number.");
            open(admin, targetUuid, targetName);
            return;
        } catch (IllegalArgumentException e) {
            admin.sendMessage("§c✘ " + e.getMessage());
            open(admin, targetUuid, targetName);
            return;
        }

        // Sync live systems
        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null && online.isOnline()) {
            syncOnlinePlayer(online, field, updated);
        } else {
            syncOfflinePlayer(targetUuid, field, updated);
        }

        saveAndSync(updated);
        admin.sendMessage("§a✔ Updated §e" + field + " §afor §e" + targetName + "§a!");
        open(admin, targetUuid, targetName);
    }

    private void syncOnlinePlayer(Player online, String field, PlayerData updated) {
        try {
            if (field.equals("experience") && plugin.getExperienceManager() != null) {
                plugin.getExperienceManager().set(online, updated.experience());
            }
            if (field.equals("money") && plugin.getSoftDependency().hasVault()) {
                plugin.getSoftDependency().setBalance(online, updated.money());
            }
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("syncOnlinePlayer failed: "
                        + e.getMessage());
            }
        }
    }

    private void syncOfflinePlayer(UUID uuid, String field, PlayerData updated) {
        try {
            if (field.equals("money") && plugin.getSoftDependency().hasVault()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                plugin.getSoftDependency().setBalance(op, updated.money());
            }
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("syncOfflinePlayer failed for "
                        + uuid + ": " + e.getMessage());
            }
        }
    }

    private PlayerData applyField(PlayerData data, String field, String raw) {
        return switch (field) {
            case "rank" -> {
                if (raw.isBlank())
                    throw new IllegalArgumentException("Rank ID cannot be blank.");
                if (!plugin.getRankManager().getRankIds().contains(raw))
                    throw new IllegalArgumentException(
                            "Unknown rank: §e" + raw + "§c. Valid ranks: "
                            + String.join(", ", plugin.getRankManager().getRankIds()));
                yield data.withRank(raw);
            }
            case "experience" -> {
                long val;
                try { val = Long.parseLong(raw); }
                catch (NumberFormatException e) {
                    throw new NumberFormatException("Experience must be a whole number, got: " + raw);
                }
                if (val < 0) throw new IllegalArgumentException("Experience cannot be negative.");
                yield data.withExperience(val);
            }
            case "money" -> {
                double val;
                try { val = Double.parseDouble(raw); }
                catch (NumberFormatException e) {
                    throw new NumberFormatException("Balance must be a number, got: " + raw);
                }
                if (val < 0) throw new IllegalArgumentException("Balance cannot be negative.");
                yield data.withMoney(val);
            }
            case "language" -> {
                if (plugin.getLangManager() != null && !plugin.getLangManager().isValidLang(raw))
                    throw new IllegalArgumentException(
                            "Unknown language: §e" + raw + "§c. Available: "
                            + String.join(", ", plugin.getLangManager().getAvailableLangs()));
                yield data.withLanguage(raw);
            }
            default -> throw new IllegalArgumentException("Unknown field: §e" + field);
        };
    }

    // ── Persist ───────────────────────────────────────────────────────────────

    private void saveAndSync(PlayerData data) {
        // Update cache immediately (thread-safe)
        plugin.getRankManager().getCacheManager().put(data.uuid(), data);

        // Async persist to storage
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getRankManager().getRepository().save(data);
            } catch (Exception e) {
                plugin.getLogger().warning("Async save failed for "
                        + data.uuid() + ": " + e.getMessage());
            }
        });

        // Notify online player if present
        Player online = Bukkit.getPlayer(data.uuid());
        if (online != null) {
            online.sendMessage("§6[RankForge] §7An admin has updated your player data.");
        }
    }

    private void resetPlayerData(Player admin, UUID targetUuid, String targetName) {
        String defaultRank = plugin.getConfig().getString("ranks.default-rank", "Guest");
        PlayerData reset   = PlayerData.defaultData(targetUuid, targetName, defaultRank);

        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null && online.isOnline()) {
            try {
                if (plugin.getExperienceManager() != null)
                    plugin.getExperienceManager().set(online, 0L);
                if (plugin.getSoftDependency().hasVault())
                    plugin.getSoftDependency().setBalance(online, 0.0);
            } catch (Exception e) {
                if (plugin.isDebug())
                    plugin.getLogger().warning("resetPlayerData sync failed: "
                            + e.getMessage());
            }
        } else {
            try {
                if (plugin.getSoftDependency().hasVault()) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(targetUuid);
                    plugin.getSoftDependency().setBalance(op, 0.0);
                }
            } catch (Exception ignored) {}
        }

        // Clear rank history on reset
        if (plugin.getHistoryManager() != null) {
            plugin.getHistoryManager().clearHistory(targetUuid);
        }

        // Clear any admin-granted /rank bypassreq completions — both the in-memory
        // registry (used for live requirement checks) and the persisted record
        // (already emptied by PlayerData.defaultData above, but this keeps runtime
        // state consistent for online players without requiring a reconnect).
        if (plugin.getBypassRegistry() != null) {
            plugin.getBypassRegistry().clearAll(targetUuid);
        }

        saveAndSync(reset);
        // Invalidate head cache for this player
        PlayerListGUI.invalidateHeadCache(targetUuid);

        admin.sendMessage("§a✔ Reset §e" + targetName + "§a's data to default!");
        open(admin, targetUuid, targetName);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

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
            meta.setLore(new ArrayList<>(Arrays.asList(lore)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makePane(Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta  meta = pane.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); pane.setItemMeta(meta); }
        return pane;
    }

    private String safeDisplayName(String rankId) {
        if (rankId == null || rankId.isBlank()) return "§7Unknown";
        try { return plugin.getRankManager().getDisplayName(rankId); }
        catch (Exception e) { return "§7" + rankId; }
    }

    // ── Static state ──────────────────────────────────────────────────────────

    public static boolean hasPendingEdit(UUID uuid) { return PENDING_EDIT.containsKey(uuid); }
    public static boolean isOpen(UUID uuid)         { return OPEN_VIEWERS.contains(uuid); }

    public static void setClosed(UUID uuid) {
        OPEN_VIEWERS.remove(uuid);
        EDITING_TARGET.remove(uuid);
        EDITING_NAME.remove(uuid);
    }

    public static boolean matchesTitle(String title) {
        return title != null && title.startsWith(TITLE_PREFIX);
    }
}
