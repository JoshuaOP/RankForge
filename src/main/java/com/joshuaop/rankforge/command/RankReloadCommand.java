package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.PlayerData;
import org.bukkit.command.CommandSender;
import java.util.Collection;

/**
 * Handles /rank reload — performs a full plugin configuration reload.
 * Reloads config.yml, ranks.yml, lang files, and all dependent managers.
 */
public class RankReloadCommand {

    private final RankForge plugin;

    public RankReloadCommand(RankForge plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender) {
        if (!sender.hasPermission("rankforge.rank.reload") && !sender.isOp()) {
            if (sender instanceof org.bukkit.entity.Player p)
                plugin.getLangManager().send(p, "no_permission");
            else
                sender.sendMessage("§cNo permission.");
            return;
        }

        sender.sendMessage("§6[RankForge] §7Initiating safe hot-reload context...");
        long start = System.currentTimeMillis();

        // 1. Intercept active data profiles to guarantee zero data loss during memory rebuilds
        CacheManager oldCache = plugin.getRankManager().getCacheManager();
        Collection<PlayerData> activeSessions = null;
        
        if (oldCache != null) {
            // Retrieve stitched runtime data structures matching ongoing online sessions
            activeSessions = oldCache.getOnlineAndUnexpired();
            
            // Proactively dump existing profiles onto flat-file disk or sync pipelines
            if (plugin.getYamlPlayerDataStorage() != null && !activeSessions.isEmpty()) {
                plugin.getYamlPlayerDataStorage().saveAll(activeSessions);
            }
        }

        // 2. Perform structural file system and implementation instance swaps
        plugin.reload();

        // 3. Re-populate the brand new cache context with our preserved session states
        if (activeSessions != null && !activeSessions.isEmpty()) {
            CacheManager newCache = plugin.getRankManager().getCacheManager();
            if (newCache != null) {
                for (PlayerData sessionData : activeSessions) {
                    // Seed newly instantiated manager cache without breaking active session hooks
                    newCache.put(sessionData.uuid(), sessionData);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;

        sender.sendMessage("§6[RankForge] §aFull reload complete §8(§e" + elapsed + "ms§8)");
        sender.sendMessage("  §7Ranks loaded: §e" + plugin.getRankManager().getRankCount());
        
        if (activeSessions != null && !activeSessions.isEmpty()) {
            sender.sendMessage("  §7Active Sessions Restructured: §e" + activeSessions.size());
        }

        plugin.getLogger().info("[Reload] Safe hot-reload triggered by " + sender.getName()
                + " completed in " + elapsed + "ms.");
    }
}
