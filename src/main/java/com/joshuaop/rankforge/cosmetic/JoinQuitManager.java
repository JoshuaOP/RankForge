package com.joshuaop.rankforge.cosmetic;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitManager implements Listener {

    private final RankForge plugin;

    public JoinQuitManager(RankForge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("cosmetic.join-quit.enabled", true)) return;

        // NOTE: If your cache is loaded asynchronously, consider moving this calculation
        // to a 1-2 tick delayed task or a data-ready callback event to avoid the race condition.
        String template = plugin.getConfig().getString("cosmetic.join-quit.join-message", 
                "{prefix}§e{player} §ajoined the server.");
        
        event.setJoinMessage(formatMessage(event.getPlayer(), template));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("cosmetic.join-quit.enabled", true)) return;

        Player player = event.getPlayer();
        String template = plugin.getConfig().getString("cosmetic.join-quit.quit-message", 
                "{prefix}§e{player} §cleft the server.");
        
        event.setQuitMessage(formatMessage(player, template));

        if (plugin.getAntiAbuseManager() != null) {
            plugin.getAntiAbuseManager().cleanup(player.getUniqueId());
        }
    }

    private String formatMessage(Player player, String template) {
        String rankId = getCurrentRankId(player);
        RankModel model = plugin.getRankManager().getRank(rankId);
        
        String prefix = model != null && model.getChatPrefix() != null && !model.getChatPrefix().isBlank()
                ? model.getChatPrefix() + " " : "";

        // Resolve crossplay clean names if config option is enabled
        String displayName = player.getName();
        if (plugin.getConfig().getBoolean("crossplay.clean-names-in-messages", true) && plugin.getApi() != null) {
            displayName = plugin.getApi().getCleanName(player);
        }

        String rawMsg = template
                .replace("{prefix}", prefix)
                .replace("{player}", displayName)
                .replace("{rank}", rankId);

        return ChatColor.translateAlternateColorCodes('&', rawMsg);
    }

    private String getCurrentRankId(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        return cache.contains(player.getUniqueId())
                ? cache.get(player.getUniqueId()).rankId()
                : plugin.getRankManager().getDefaultRankId();
    }
}
