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

        // 1. Flush live tracker counters into cache so the YAML safety save below
        //    captures the most current block-break and playtime values.
        if (plugin.getBlockBreakTracker() != null) plugin.getBlockBreakTracker().flushAll();
        if (plugin.getPlaytimeTracker()    != null) plugin.getPlaytimeTracker().flushAll();

        // 2. Take a snapshot of active sessions and write a YAML safety backup.
        //    This is a disk-level guard only — the CacheManager itself is never
        //    destroyed during reload, so in-memory data is already preserved.
        CacheManager cache = plugin.getRankManager().getCacheManager();
        Collection<PlayerData> activeSessions = null;

        if (cache != null) {
            activeSessions = cache.getOnlineAndUnexpired();
            if (plugin.getYamlPlayerDataStorage() != null && !activeSessions.isEmpty()) {
                plugin.getYamlPlayerDataStorage().saveAll(activeSessions);
            }
        }

        // 3. Perform the reload (config, ranks, lang, cosmetics, tasks, etc.).
        //    plugin.reload() internally calls rankManager.repairOrphanedRanks()
        //    which fixes any player whose rank ID no longer exists after a
        //    ranks.yml change.  The CacheManager is the same instance throughout —
        //    do NOT re-populate the cache afterwards or those repairs are undone.
        plugin.reload();

        long elapsed = System.currentTimeMillis() - start;

        sender.sendMessage("§6[RankForge] §aFull reload complete §8(§e" + elapsed + "ms§8)");
        sender.sendMessage("  §7Ranks loaded: §e" + plugin.getRankManager().getRankCount());

        if (activeSessions != null && !activeSessions.isEmpty()) {
            sender.sendMessage("  §7Active sessions preserved: §e" + activeSessions.size());
        }

        plugin.getLogger().info("[Reload] Safe hot-reload triggered by " + sender.getName()
                + " completed in " + elapsed + "ms.");
    }
}
