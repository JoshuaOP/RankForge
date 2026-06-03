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
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI that lists all players currently cached in memory or active on the server.
 * Clicking a player head opens the PlayerDataEditorGUI for that player.
 *
 * Stability guarantees:
 *  - All player head items are null-checked before placement.
 *  - Offline player OfflinePlayer references are cached to avoid repeated lookups.
 *  - Slot bounds are validated before every setItem call.
 *  - Pagination is clamped so a stale page index never causes an out-of-bounds read.
 *  - Corrupted / incomplete PlayerData entries are skipped gracefully.
 */
public class PlayerListGUI {

    public static final String TITLE    = "§8✦ §9Player List §8✦";

    private static final int PAGE_SIZE  = 45;
    private static final int PREV_SLOT  = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT  = 53;
    private static final int INV_SIZE   = 54;

    /** Per-admin current page. Cleared on close or click-through to editor. */
    private static final Map<UUID, Integer> PAGE_MAP = new ConcurrentHashMap<>();

    /**
     * Short-lived OfflinePlayer reference cache keyed by UUID.
     * Prevents repeated Bukkit.getOfflinePlayer() calls (which can hit the filesystem
     * for unknown UUIDs on some implementations).
     */
    private static final Map<UUID, OfflinePlayer> OFFLINE_CACHE = new ConcurrentHashMap<>();

    private final RankForge plugin;

    public PlayerListGUI(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void open(Player admin) {
        open(admin, PAGE_MAP.getOrDefault(admin.getUniqueId(), 0));
    }

    public void open(Player admin, int page) {
        if (admin == null || !admin.isOnline()) return;

        List<PlayerData> allPlayers = safeCollect();
        int totalPages = Math.max(1, (int) Math.ceil(allPlayers.size() / (double) PAGE_SIZE));
        int safePage   = Math.max(0, Math.min(page, totalPages - 1));

        PAGE_MAP.put(admin.getUniqueId(), safePage);

        Inventory inv = Bukkit.createInventory(null, INV_SIZE, TITLE);
        populatePlayers(inv, allPlayers, safePage);
        buildNavigation(inv, safePage, totalPages, allPlayers.size());

        plugin.getSoundManager().playOpen(admin);
        admin.openInventory(inv);
    }

    // ── Population ────────────────────────────────────────────────────────────

    private void populatePlayers(Inventory inv, List<PlayerData> profiles, int page) {
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, profiles.size());

        for (int i = start; i < end; i++) {
            int slot = i - start;
            if (slot < 0 || slot >= PAGE_SIZE) continue;

            PlayerData data = profiles.get(i);
            if (data == null || data.uuid() == null) continue;

            ItemStack skull = buildSkull(data);
            if (skull != null) {
                inv.setItem(slot, skull);
            }
        }
    }

    private ItemStack buildSkull(PlayerData data) {
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta  = (SkullMeta) skull.getItemMeta();
            if (meta == null) return null;

            // Use cached OfflinePlayer reference to avoid repeated lookups
            OfflinePlayer op = OFFLINE_CACHE.computeIfAbsent(
                    data.uuid(), Bukkit::getOfflinePlayer);

            meta.setOwningPlayer(op);

            String displayName = (data.playerName() != null && !data.playerName().isBlank())
                    ? data.playerName() : "Unknown";
            meta.setDisplayName("§e" + displayName);

            String rankDisplay = "§7Unknown";
            try {
                rankDisplay = plugin.getRankManager().getDisplayName(data.rankId());
            } catch (Exception ignored) {}

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Rank:     " + rankDisplay);
            lore.add("§7Language: §f" + safeLanguage(data.language()));
            lore.add("§7UUID:     §8" + data.uuid().toString().substring(0, 8) + "…");
            lore.add("");
            lore.add("§a▶ Click to edit this player's data.");

            meta.setLore(lore);
            skull.setItemMeta(meta);
            return skull;
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[PlayerListGUI] Failed to build skull for "
                        + data.uuid() + ": " + e.getMessage());
            }
            return null;
        }
    }

    // ── Navigation Bar ────────────────────────────────────────────────────────

    private void buildNavigation(Inventory inv, int page, int totalPages, int totalPlayers) {
        ItemStack border = makeNamedItem(Material.GRAY_STAINED_GLASS_PANE, " ");

        for (int i = 45; i < INV_SIZE; i++) {
            if (i != PREV_SLOT && i != CLOSE_SLOT && i != NEXT_SLOT) {
                inv.setItem(i, border);
            }
        }

        inv.setItem(PREV_SLOT, page > 0
                ? makeNamedItem(Material.ARROW, "§e§l← Previous Page",
                        "§7Page " + page + " §8/ §7" + totalPages)
                : border);

        inv.setItem(CLOSE_SLOT, makeNamedItem(Material.BARRIER, "§c§lClose",
                "§7Showing §e" + totalPlayers + " §7cached players",
                "§7Page §e" + (page + 1) + " §7of §e" + totalPages));

        inv.setItem(NEXT_SLOT, page < totalPages - 1
                ? makeNamedItem(Material.ARROW, "§e§lNext Page →",
                        "§7Page " + (page + 2) + " §8/ §7" + totalPages)
                : border);
    }

    // ── Click Handling ────────────────────────────────────────────────────────

    public void handleClick(Player admin, int slot, ItemStack clickedItem) {
        if (admin == null || !admin.isOnline()) return;

        UUID adminUuid = admin.getUniqueId();

        if (slot == CLOSE_SLOT) {
            PAGE_MAP.remove(adminUuid);
            admin.closeInventory();
            return;
        }

        int currentPage = PAGE_MAP.getOrDefault(adminUuid, 0);

        if (slot == PREV_SLOT) {
            if (currentPage > 0) {
                plugin.getSoundManager().playClick(admin);
                open(admin, currentPage - 1);
            }
            return;
        }

        if (slot == NEXT_SLOT) {
            plugin.getSoundManager().playClick(admin);
            open(admin, currentPage + 1);
            return;
        }

        // Only player-head slots are actionable
        if (slot < 0 || slot >= PAGE_SIZE) return;
        if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
        if (meta == null) {
            admin.sendMessage("§c[RankForge] Could not read player data from this slot.");
            return;
        }

        OfflinePlayer owning = meta.getOwningPlayer();
        if (owning == null) {
            admin.sendMessage("§c[RankForge] This player head has no owner data.");
            return;
        }

        UUID   targetUuid = owning.getUniqueId();
        String targetName = meta.getDisplayName() != null
                ? org.bukkit.ChatColor.stripColor(meta.getDisplayName()) : "Unknown";

        if (targetUuid == null) {
            admin.sendMessage("§c[RankForge] Could not resolve player UUID from this head.");
            return;
        }

        PAGE_MAP.remove(adminUuid);
        admin.closeInventory();
        new PlayerDataEditorGUI(plugin).open(admin, targetUuid, targetName);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Collects all PlayerData from cache, filtering out null and corrupt entries.
     * Sorts by player name for stable pagination across opens.
     */
    private List<PlayerData> safeCollect() {
        List<PlayerData> result = new ArrayList<>();
        try {
            Collection<PlayerData> raw = plugin.getRankManager().getCacheManager().all();
            for (PlayerData pd : raw) {
                if (pd == null || pd.uuid() == null) continue;
                result.add(pd);
            }
            result.sort(Comparator.comparing(
                    pd -> (pd.playerName() != null ? pd.playerName() : ""),
                    String.CASE_INSENSITIVE_ORDER));
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[PlayerListGUI] Failed to collect player list: " + e.getMessage());
            }
        }
        return result;
    }

    private String safeLanguage(String lang) {
        return (lang != null && !lang.isBlank()) ? lang.toUpperCase() : "EN";
    }

    private ItemStack makeNamedItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Invalidate the OfflinePlayer reference cache for a specific UUID. */
    public static void invalidateHeadCache(UUID uuid) {
        if (uuid != null) OFFLINE_CACHE.remove(uuid);
    }

    /** Clear the entire head cache — call on full plugin reload. */
    public static void clearHeadCache() {
        OFFLINE_CACHE.clear();
    }
}
