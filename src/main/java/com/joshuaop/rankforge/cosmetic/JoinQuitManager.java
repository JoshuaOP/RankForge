package com.joshuaop.rankforge.cosmetic;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Custom per-rank join and quit messages.
 * Replaces the default Bukkit join/quit messages with rank-themed ones.
 */
public class JoinQuitManager implements Listener {

    private final RankForge plugin;

    public JoinQuitManager(RankForge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("cosmetic.join-quit.enabled", true)) return;

        Player    player  = event.getPlayer();
        String    rankId  = getCurrentRankId(player);
        RankModel model   = plugin.getRankManager().getRankData(rankId);
        String    prefix  = model != null && !model.getChatPrefix().isBlank()
                            ? model.getChatPrefix() + " " : "";

        String template = plugin.getConfig().getString("cosmetic.join-quit.join-message",
                "{prefix}§e{player} §ajoined the server.");
        String msg = template
                .replace("{prefix}", prefix)
                .replace("{player}", player.getName())
                .replace("{rank}", rankId);

        event.setJoinMessage(msg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("cosmetic.join-quit.enabled", true)) return;

        Player    player  = event.getPlayer();
        String    rankId  = getCurrentRankId(player);
        RankModel model   = plugin.getRankManager().getRankData(rankId);
        String    prefix  = model != null && !model.getChatPrefix().isBlank()
                            ? model.getChatPrefix() + " " : "";

        String template = plugin.getConfig().getString("cosmetic.join-quit.quit-message",
                "{prefix}§e{player} §cleft the server.");
        String msg = template
                .replace("{prefix}", prefix)
                .replace("{player}", player.getName())
                .replace("{rank}", rankId);

        event.setQuitMessage(msg);

        // Cleanup anti-abuse tracking
        plugin.getAntiAbuseManager().cleanup(player.getUniqueId());
        plugin.getTaskScheduler().async(() ->
                plugin.getRankManager().getCacheManager().scheduleCleanup(player.getUniqueId()));
    }

    private String getCurrentRankId(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        return cache.contains(player.getUniqueId())
                ? cache.get(player.getUniqueId()).rankId()
                : plugin.getRankManager().getDefaultRankId();
    }
}
