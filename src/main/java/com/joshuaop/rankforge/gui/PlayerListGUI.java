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
 * Admin GUI that shows all known players with their rank data.
 * Accessed via /rank playerlist
 *
 * Layout (54 slots):
 *   Slots 0-44  — Player heads (paged, 45 per page)
 *   Slot 45     — Previous page
 *   Slot 49     — Info item
 *   Slot 53     — Next page
 *   Slot 47     — Close
 */
public class PlayerListGUI {

    public static final String TITLE_PREFIX = "§8✦ §bPlayer List §8✦";

    private static final Set<UUID>           OPEN_VIEWERS = new HashSet<>();
    private static final Map<UUID, Integer>  CURRENT_PAGE = new HashMap<>();
    private static final Map<UUID, List<PlayerData>> PAGE_DATA = new HashMap<>();

    private static final int PAGE_SIZE = 45;

    private final RankForge plugin;

    public PlayerListGUI(RankForge plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<PlayerData> allPlayers = collectAllPlayers();
        PAGE_DATA.put(player.getUniqueId(), allPlayers);
        CURRENT_PAGE.put(player.getUniqueId(), page);

        int maxPage = Math.max(0, (allPlayers.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));

        String title = TITLE_PREFIX + " §8[§7" + (page + 1) + "§8/§7" + (maxPage + 1) + "§8]";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, allPlayers.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildPlayerHead(allPlayers.get(i)));
        }

        buildNavigation(inv, page, maxPage, allPlayers.size());

        plugin.getSoundManager().playOpen(player);
        player.openInventory(inv);
        OPEN_VIEWERS.add(player.getUniqueId());
    }

    public void open(Player player) {
        open(player, 0);
    }

    private List<PlayerData> collectAllPlayers() {
        Map<UUID, PlayerData> combined = new LinkedHashMap<>();

        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml != null) {
            for (PlayerData pd : yaml.loadAll()) {
                combined.put(pd.uuid(), pd);
            }
        }

        for (PlayerData pd : plugin.getRankManager().getCacheManager().all()) {
            combined.put(pd.uuid(), pd);
        }

        return new ArrayList<>(combined.values());
    }

    private ItemStack buildPlayerHead(PlayerData data) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        OfflinePlayer op = Bukkit.getOfflinePlayer(data.uuid());
        meta.setOwningPlayer(op);

        String rankDisplay = plugin.getRankManager().getDisplayName(data.rankId());
        meta.setDisplayName("§b§l" + data.playerName());
        meta.setLore(Arrays.asList(
                "",
                "§7Rank:       " + rankDisplay + " §8(" + data.rankId() + ")",
                "§7Experience: §e" + data.experience(),
                "§7Money:      §a$" + String.format("%,.2f", data.money()),
                "§7Language:   §f" + data.language(),
                "",
                "§e▶ Click to edit player data"
        ));
        skull.setItemMeta(meta);
        return skull;
    }

    private void buildNavigation(Inventory inv, int page, int maxPage, int total) {
        if (page > 0) {
            inv.setItem(45, makeBtn(Material.ARROW, "§a§l← Previous Page",
                    new String[]{"§7Go to page §e" + page}));
        } else {
            inv.setItem(45, RankItemBuilder.glassPane(Material.GRAY_STAINED_GLASS_PANE));
        }

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta  im   = info.getItemMeta();
        if (im != null) {
            im.setDisplayName("§6§lPlayer List");
            im.setLore(Arrays.asList(
                    "§7Total players: §e" + total,
                    "§7Page: §e" + (page + 1) + " §7/ §e" + (maxPage + 1)
            ));
            info.setItemMeta(im);
        }
        inv.setItem(49, info);

        inv.setItem(47, makeBtn(Material.BARRIER, "§c§lClose", new String[]{"§7Close this GUI"}));

        if (page < maxPage) {
            inv.setItem(53, makeBtn(Material.ARROW, "§a§lNext Page →",
                    new String[]{"§7Go to page §e" + (page + 2)}));
        } else {
            inv.setItem(53, RankItemBuilder.glassPane(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    public void handleClick(Player player, int slot, String title) {
        int page        = CURRENT_PAGE.getOrDefault(player.getUniqueId(), 0);
        List<PlayerData> allPlayers = PAGE_DATA.getOrDefault(player.getUniqueId(), new ArrayList<>());
        int maxPage     = allPlayers.isEmpty() ? 0 : (allPlayers.size() - 1) / PAGE_SIZE;

        switch (slot) {
            case 45 -> { if (page > 0)       { player.closeInventory(); open(player, page - 1); } }
            case 47 -> player.closeInventory();
            case 53 -> { if (page < maxPage) { player.closeInventory(); open(player, page + 1); } }
            default -> {
                if (slot < 0 || slot > 44) return;
                int dataIndex = page * PAGE_SIZE + slot;
                if (dataIndex >= allPlayers.size()) return;
                PlayerData data = allPlayers.get(dataIndex);
                player.closeInventory();
                new PlayerDataEditorGUI(plugin).open(player, data.uuid(), data.playerName());
            }
        }
    }

    private ItemStack makeBtn(Material mat, String name, String[] lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta); }
        return item;
    }

    public static boolean isOpen(UUID uuid)    { return OPEN_VIEWERS.contains(uuid); }
    public static void    setClosed(UUID uuid) { OPEN_VIEWERS.remove(uuid); CURRENT_PAGE.remove(uuid); PAGE_DATA.remove(uuid); }
    public static boolean matchesTitle(String title) { return title.startsWith(TITLE_PREFIX); }
}
