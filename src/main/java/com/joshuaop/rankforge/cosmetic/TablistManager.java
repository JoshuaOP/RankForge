package com.joshuaop.rankforge.cosmetic;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.entity.Player;

/**
 * Tablist rank formatting — sets the player's tab display name
 * with their rank prefix and player name.
 */
public class TablistManager {

    private final RankForge plugin;

    public TablistManager(RankForge plugin) {
        this.plugin = plugin;
    }

    /** Update the player's tablist display name based on their rank. */
    public void update(Player player, String rankId) {
        if (!plugin.getConfig().getBoolean("cosmetic.tablist.enabled", true)) return;

        RankModel model = plugin.getRankManager().getRankData(rankId);
        String prefix   = model != null && !model.getChatPrefix().isBlank()
                          ? model.getChatPrefix() + " "
                          : "";
        String format   = plugin.getConfig().getString(
                "cosmetic.tablist.format", "{prefix}{player}");
        String name = format
                .replace("{prefix}", prefix)
                .replace("{player}", player.getName());

        // Trim to Bukkit's 16-char limit for player list name
        player.setPlayerListName(name.length() > 48 ? name.substring(0, 48) : name);
    }

    /** Reset a player's tablist name to their plain username. */
    public void reset(Player player) {
        player.setPlayerListName(player.getName());
    }

    /** Update all online players' tablist names. */
    public void updateAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            var cache = plugin.getRankManager().getCacheManager();
            String rankId = cache.contains(p.getUniqueId())
                    ? cache.get(p.getUniqueId()).rankId()
                    : plugin.getRankManager().getDefaultRankId();
            update(p, rankId);
        }
    }
}
