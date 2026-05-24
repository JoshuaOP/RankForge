package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Handles /rank version — shows plugin info, soft-dependency status, and storage type.
 */
public class RankVersionCommand {

    private final RankForge plugin;

    public RankVersionCommand(RankForge plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender) {
        String pluginVer  = plugin.getDescription().getVersion();
        String mcVer      = Bukkit.getBukkitVersion().split("-")[0];
        String storageStr = plugin.getDatabaseManager().isConnected() ? "§aMySQL" : "§eYAML File";

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("  §6§lRankForge §r§7v§a" + pluginVer);
        sender.sendMessage("  §7Author:   §eJoshuaOP");
        sender.sendMessage("  §7GitHub:   §bgithub.com/JoshuaOP/RankForge");
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("  §7MC Version:     §e" + mcVer);
        sender.sendMessage("  §7Ranks loaded:   §e" + plugin.getRankManager().getRankCount());
        sender.sendMessage("  §7Storage:        " + storageStr);
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("  §7Vault:          " + (plugin.getSoftDependency().hasVault()
                ? "§a✔ Hooked" : "§7Not installed"));
        sender.sendMessage("  §7LuckPerms:      " + (plugin.getSoftDependency().hasLuckPerms()
                ? "§a✔ Hooked" : "§7Not installed"));
        sender.sendMessage("  §7PlaceholderAPI: " + (plugin.getSoftDependency().hasPapi()
                ? "§a✔ Hooked" : "§7Not installed"));
        sender.sendMessage("§8§m                                        ");
    }
}
