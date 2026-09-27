package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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

        if (!isPluginGUI(gc, title)) return;

        // Block out-of-bounds clicks completely
        if (event.getRawSlot() < 0) return;

        int topInventorySize = event.getView().getTopInventory().getSize();

        // Cancel top-inventory actions by default to preserve custom GUI layout matrix
        if (event.getRawSlot() < topInventorySize) {
            event.setCancelled(true);
        } else {
            // Block shift-clicks and double-clicks from pulling/moving items into or out of top inventory
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
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

        boolean isRank       = gc.playerTitle() != null && gc.playerTitle().equals(title);
        boolean isAdmin      = gc.adminTitle() != null && gc.adminTitle().equals(title);
        boolean isDrag       = gc.dragDropTitle() != null && gc.dragDropTitle().equals(title);
        boolean isDetail     = title != null && gc.detailTitlePrefix() != null && title.startsWith(gc.detailTitlePrefix());
        boolean isPlayerList = gc.playerListTitle() != null && gc.playerListTitle().equals(title);
        boolean isPlayerData = title != null && gc.playerDataEditorTitlePrefix() != null && title.startsWith(gc.playerDataEditorTitlePrefix());

        // Safe operational dispatch to stateless or centrally managed instances
        if (isRank)       new AnimatedRankTreeGUI(plugin).handleClick(player, slot);
        if (isAdmin)      new AdminRankEditorGUI(plugin).handleClick(player, slot, title);
        if (isDrag)       new DragDropRankEditorGUI(plugin).handleClick(player, slot);
        if (isDetail)     new RankDetailEditorGUI(plugin).handleClick(player, slot);
        if (isPlayerList) new PlayerListGUI(plugin).handleClick(player, slot, event.getCurrentItem());
        if (isPlayerData) new PlayerDataEditorGUI(plugin).handleClick(player, slot);
    }

    // ── Inventory Drag Exploit Protection ────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getView().getTitle();
        GUIConfig gc = plugin.getGuiConfig();

        if (!isPluginGUI(gc, title)) return;

        int topInventorySize = event.getView().getTopInventory().getSize();

        // Cancel the drag event if any dragged slots overlap with the top GUI inventory
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventorySize) {
                event.setCancelled(true);
                break;
            }
        }
    }

    // ── Inventory Close ───────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        UUID uuid = player.getUniqueId();

        GUIConfig gc = plugin.getGuiConfig();
        if (gc.playerTitle() != null && gc.playerTitle().equals(title))                                       AnimatedRankTreeGUI.setClosed(uuid);
        if (gc.adminTitle() != null && gc.adminTitle().equals(title))                                         AdminRankEditorGUI.setClosed(uuid);
        if (gc.dragDropTitle() != null && gc.dragDropTitle().equals(title))                                   DragDropRankEditorGUI.setClosed(uuid);
        if (title != null && gc.detailTitlePrefix() != null && title.startsWith(gc.detailTitlePrefix()))       RankDetailEditorGUI.setClosed(uuid);
        if (gc.playerListTitle() != null && gc.playerListTitle().equals(title))                               PlayerListGUI.closeFor(uuid);
        if (title != null && gc.playerDataEditorTitlePrefix() != null && title.startsWith(gc.playerDataEditorTitlePrefix())) PlayerDataEditorGUI.setClosed(uuid);
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
            
            // Re-verify flags against live maps on sync thread execution to prevent race conditions
            if (RankDetailEditorGUI.hasPendingEdit(uuid)) {
                new RankDetailEditorGUI(plugin).applyEdit(player, input);
            } else if (RankDetailEditorGUI.hasPendingCreate(uuid)) {
                new RankDetailEditorGUI(plugin).applyCreate(player, input);
            } else if (RankDetailEditorGUI.hasPendingDelete(uuid)) {
                new RankDetailEditorGUI(plugin).applyDelete(player, input);
            } else if (PlayerDataEditorGUI.hasPendingEdit(uuid)) {
                new PlayerDataEditorGUI(plugin).applyEdit(player, input);
            }
        });
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    private boolean isPluginGUI(GUIConfig gc, String title) {
        if (title == null || gc == null) return false;
        return (gc.playerTitle() != null && gc.playerTitle().equals(title))
                || (gc.adminTitle() != null && gc.adminTitle().equals(title))
                || (gc.dragDropTitle() != null && gc.dragDropTitle().equals(title))
                || (gc.detailTitlePrefix() != null && title.startsWith(gc.detailTitlePrefix()))
                || (gc.playerListTitle() != null && gc.playerListTitle().equals(title))
                || (gc.playerDataEditorTitlePrefix() != null && title.startsWith(gc.playerDataEditorTitlePrefix()));
    }
}
