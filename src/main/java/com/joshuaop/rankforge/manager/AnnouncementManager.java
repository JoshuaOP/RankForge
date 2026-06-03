package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Handles all rank-up announcements: broadcast chat, title, and action bar.
 */
public class AnnouncementManager {

    private final RankForge plugin;

    public AnnouncementManager(RankForge plugin) {
        this.plugin = plugin;
    }

    /** Broadcast a full rankup announcement for the given player and rank. */
    public void sendRankup(Player player, String rankDisplay) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("announcements.enabled", true)) return;

        if (cfg.getBoolean("announcements.rankup.broadcast", true)) {
            String msg = colorize(cfg.getString("announcements.rankup.message",
                    "§6[RankForge] §e%player% §aranked up to §6%rank%§a!"))
                    .replace("%player%", player.getName())
                    .replace("%rank%", rankDisplay);
            Bukkit.broadcastMessage(msg);
        }

        if (cfg.getBoolean("announcements.rankup.title.enabled", true)) {
            String title    = colorize(cfg.getString("announcements.rankup.title.title",   "§6✦ Rank Up! ✦"));
            String subtitle = colorize(cfg.getString("announcements.rankup.title.subtitle", "§eYou are now §a%rank%"))
                    .replace("%player%", player.getName())
                    .replace("%rank%", rankDisplay);
            int fadeIn  = cfg.getInt("announcements.rankup.title.fade-in",  10);
            int stay    = cfg.getInt("announcements.rankup.title.stay",     60);
            int fadeOut = cfg.getInt("announcements.rankup.title.fade-out", 10);
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }

        if (cfg.getBoolean("announcements.rankup.action-bar.enabled", true)) {
            String bar = colorize(cfg.getString("announcements.rankup.action-bar.message",
                    "§a✦ You ranked up to §e%rank% §a✦"))
                    .replace("%rank%", rankDisplay);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar));
        }
    }

    /** Send a custom admin announcement to all players. */
    public void broadcast(String message) {
        Bukkit.broadcastMessage(colorize(message));
    }

    private String colorize(String msg) {
        return msg == null ? "" : msg.replace("&", "§");
    }
}
