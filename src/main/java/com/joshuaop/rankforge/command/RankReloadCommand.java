package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.command.CommandSender;

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

        long start = System.currentTimeMillis();
        plugin.reload();
        long elapsed = System.currentTimeMillis() - start;

        sender.sendMessage("§6[RankForge] §aFull reload complete §8(§e" + elapsed + "ms§8)");
        sender.sendMessage("  §7Ranks loaded: §e" + plugin.getRankManager().getRankCount());
        plugin.getLogger().info("[Reload] Triggered by " + sender.getName()
                + " in " + elapsed + "ms.");
    }
}
