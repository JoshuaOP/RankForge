package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.command.CommandSender;

/**
 * Handles /rank version — shows core plugin info, author, and repository links.
 */
public class RankVersionCommand {

    private final RankForge plugin;

    public RankVersionCommand(RankForge plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender) {
        String pluginVer = plugin.getDescription().getVersion();

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("  §6§lRankForge §r§7v§a" + pluginVer);
        sender.sendMessage("  §7Author:   §eJoshuaOP");
        sender.sendMessage("  §7GitHub:   §bgithub.com/JoshuaOP/RankForge");
        sender.sendMessage("§8§m                                        ");
    }
}
