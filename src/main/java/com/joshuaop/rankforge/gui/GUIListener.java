package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Intercepts all inventory interactions for RankForge GUIs.
 * Also handles chat-based input for rank and player data editors.
 */
public class GUIListener implements Listener {

    private final RankForge plugin;

    public GUIListener(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Inventory Clicks ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        GUIConfig gc = plugin.getGuiConfig();

        // Determine targets safely — resolved from gui.yml at event time so
        // titles updated via /rank reload are always matched correctly.
        boolean isRank       = gc.playerTitle().equals(title);
        boolean isAdmin      = gc.adminTitle().equals(title);
        boolean isDrag       = gc.dragDropTitle().equals(title);
        boolean isDetail     = title != null && title.startsWith(gc.detailTitlePrefix());
        boolean isPlayerList = gc.playerListTitle().equals(title);
        boolean isPlayerData = title != null && title.startsWith(gc.playerDataEditorTitlePrefix());

        if (!isRank && !isAdmin && !isDrag && !isDetail && !isPlayerList && !isPlayerData) return;

        // Block out-of-bounds clicks completely
        if (event.getRawSlot() < 0) return;

        // Cancel top-inventory actions by default to preserve custom GUI layout matrix
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
        } else {
            // Optional: allow/deny bottom inventory shift-clicking interaction
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return; // Allow regular un-shifted actions inside player's personal bag slots
        }

        // Rate-limit processing via GUI click-shield validation
        if (!plugin.getGuiClickShieldManager().allow(player.getUniqueId())) {
            plugin.getLangManager().send(player, "gui_click_fast");
            return;
        }

        plugin.getSoundManager().playClick(player);
        int slot = event.getRawSlot();

        // Safe operational dispatch to stateless or centrally managed instances
        if (isRank)       new AnimatedRankTreeGUI(plugin).handleClick(player, slot);
        if (isAdmin)      new AdminRankEditorGUI(plugin).handleClick(player, slot, title);
        if (isDrag)       new DragDropRankEditorGUI(plugin).handleClick(player, slot);
        if (isDetail)     new RankDetailEditorGUI(plugin).handleClick(player, slot);
        if (isPlayerList) new PlayerListGUI(plugin).handleClick(player, slot, event.getCurrentItem()); // Fixed: Passes clicked item stack instead of String
        if (isPlayerData) new PlayerDataEditorGUI(plugin).handleClick(player, slot);
    }

    // ── Inventory Close ───────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        UUID uuid = player.getUniqueId();

        GUIConfig gc = plugin.getGuiConfig();
        if (gc.playerTitle().equals(title))                                  AnimatedRankTreeGUI.setClosed(uuid);
        if (gc.adminTitle().equals(title))                                   AdminRankEditorGUI.setClosed(uuid);
        if (gc.dragDropTitle().equals(title))                                DragDropRankEditorGUI.setClosed(uuid);
        if (title != null && title.startsWith(gc.detailTitlePrefix()))       RankDetailEditorGUI.setClosed(uuid);
        if (gc.playerListTitle().equals(title))                              PlayerListGUI.closeFor(uuid);
        if (title != null && title.startsWith(gc.playerDataEditorTitlePrefix())) PlayerDataEditorGUI.setClosed(uuid);
    }

    // ── Chat-based editing ────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // Evaluate map tracking within the async block to capture true structural visibility
        boolean rankEdit       = RankDetailEditorGUI.hasPendingEdit(uuid);
        boolean rankCreate     = RankDetailEditorGUI.hasPendingCreate(uuid);
        boolean rankDelete     = RankDetailEditorGUI.hasPendingDelete(uuid);
        boolean playerDataEdit = PlayerDataEditorGUI.hasPendingEdit(uuid);

        if (!rankEdit && !rankCreate && !rankDelete && !playerDataEdit) return;

        // Intercept text stream immediately
        event.setCancelled(true);
        String input = event.getMessage();

        // Bounce context execution back to primary synchronization thread pool
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            
            // Re-verify flags on sync-thread execution to mitigate race conditions
            if (rankEdit)       new RankDetailEditorGUI(plugin).applyEdit(player, input);
            else if (rankCreate)     new RankDetailEditorGUI(plugin).applyCreate(player, input);
            else if (rankDelete)     new RankDetailEditorGUI(plugin).applyDelete(player, input);
            else if (playerDataEdit) new PlayerDataEditorGUI(plugin).applyEdit(player, input);
        });
    }
}
