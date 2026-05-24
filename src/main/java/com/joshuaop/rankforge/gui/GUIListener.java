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

    private final RankForge             plugin;
    private final AnimatedRankTreeGUI   rankGUI;
    private final AdminRankEditorGUI    adminGUI;
    private final DragDropRankEditorGUI dragDropGUI;
    private final RankDetailEditorGUI   detailGUI;
    private final PlayerListGUI         playerListGUI;
    private final PlayerDataEditorGUI   playerDataEditorGUI;

    public GUIListener(RankForge plugin) {
        this.plugin             = plugin;
        this.rankGUI            = new AnimatedRankTreeGUI(plugin);
        this.adminGUI           = new AdminRankEditorGUI(plugin);
        this.dragDropGUI        = new DragDropRankEditorGUI(plugin);
        this.detailGUI          = new RankDetailEditorGUI(plugin);
        this.playerListGUI      = new PlayerListGUI(plugin);
        this.playerDataEditorGUI = new PlayerDataEditorGUI(plugin);
    }

    // ── Inventory Clicks ──────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title        = event.getView().getTitle();
        boolean isRank      = AnimatedRankTreeGUI.TITLE.equals(title);
        boolean isAdmin     = AdminRankEditorGUI.TITLE.equals(title);
        boolean isDrag      = DragDropRankEditorGUI.TITLE.equals(title);
        boolean isDetail    = RankDetailEditorGUI.matchesTitle(title);
        boolean isPlayerList = PlayerListGUI.matchesTitle(title);
        boolean isPlayerData = PlayerDataEditorGUI.matchesTitle(title);

        if (!isRank && !isAdmin && !isDrag && !isDetail && !isPlayerList && !isPlayerData) return;

        // Guard check: prevent out-of-bounds calculations when clicking outside the window interface boundary
        if (event.getRawSlot() < 0) return;

        event.setCancelled(true);

        if (!plugin.getGuiClickShieldManager().allow(player.getUniqueId())) {
            plugin.getLangManager().send(player, "gui_click_fast");
            return;
        }

        plugin.getSoundManager().playClick(player);

        if (isRank)       rankGUI.handleClick(player, event.getRawSlot());
        if (isAdmin)      adminGUI.handleClick(player, event.getRawSlot(), title);
        if (isDrag)       dragDropGUI.handleClick(player, event.getRawSlot());
        if (isDetail)     detailGUI.handleClick(player, event.getRawSlot());
        if (isPlayerList) playerListGUI.handleClick(player, event.getRawSlot(), title);
        if (isPlayerData) playerDataEditorGUI.handleClick(player, event.getRawSlot());
    }

    // ── Inventory Close ───────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (AnimatedRankTreeGUI.TITLE.equals(title))    AnimatedRankTreeGUI.setClosed(player.getUniqueId());
        if (AdminRankEditorGUI.TITLE.equals(title))     AdminRankEditorGUI.setClosed(player.getUniqueId());
        if (DragDropRankEditorGUI.TITLE.equals(title))  DragDropRankEditorGUI.setClosed(player.getUniqueId());
        if (RankDetailEditorGUI.matchesTitle(title))    RankDetailEditorGUI.setClosed(player.getUniqueId());
        if (PlayerListGUI.matchesTitle(title))          PlayerListGUI.setClosed(player.getUniqueId());
        if (PlayerDataEditorGUI.matchesTitle(title))    PlayerDataEditorGUI.setClosed(player.getUniqueId());
    }

    // ── Chat-based editing ────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        boolean rankEdit       = RankDetailEditorGUI.hasPendingEdit(uuid);
        boolean playerDataEdit = PlayerDataEditorGUI.hasPendingEdit(uuid);

        if (!rankEdit && !playerDataEdit) return;

        event.setCancelled(true);
        String input = event.getMessage();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (rankEdit)       detailGUI.applyEdit(player, input);
            if (playerDataEdit) playerDataEditorGUI.applyEdit(player, input);
        });
    }
}
