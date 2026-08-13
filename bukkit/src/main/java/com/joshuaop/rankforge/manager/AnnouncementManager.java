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

        // 1. Global Server Broadcast Handling
        if (cfg.getBoolean("announcements.rankup.broadcast", true)) {
            String rawMessage = cfg.getString("announcements.rankup.message",
                    "&6[RankForge] &e%player% &aranked up to &6%rank%&a!");
            
            String msg = colorize(applyPlaceholders(rawMessage, player, rankDisplay));
            Bukkit.broadcastMessage(msg);
        }

        // 2. Full-Screen Title Display Handling
        if (cfg.getBoolean("announcements.rankup.title.enabled", true)) {
            String rawTitle = cfg.getString("announcements.rankup.title.title", "&6✦ Rank Up! ✦");
            String rawSubtitle = cfg.getString("announcements.rankup.title.subtitle", "&eYou are now &a%rank%");

            // BUG FIX: Placeholders are now properly expanded on BOTH title and subtitle fields
            String title = colorize(applyPlaceholders(rawTitle, player, rankDisplay));
            String subtitle = colorize(applyPlaceholders(rawSubtitle, player, rankDisplay));

            int fadeIn  = cfg.getInt("announcements.rankup.title.fade-in",  10);
            int stay    = cfg.getInt("announcements.rankup.title.stay",     60);
            int fadeOut = cfg.getInt("announcements.rankup.title.fade-out", 10);
            
            // BUG FIX: Use cross-compatible title mechanics to prevent compilation and runtime deprecation issues
            try {
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            } catch (NoSuchMethodError e) {
                // Retro-fallback interface handling for legacy infrastructure environments
                player.sendTitle(title, subtitle);
            }
        }

        // 3. Action Bar Message Notification
        if (cfg.getBoolean("announcements.rankup.action-bar.enabled", true)) {
            String rawBar = cfg.getString("announcements.rankup.action-bar.message", "&a✦ You ranked up to &e%rank% &a✦");
            String bar = colorize(applyPlaceholders(rawBar, player, rankDisplay));
            
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar));
        }
    }

    /** Send a custom admin announcement to all players. */
    public void broadcast(String message) {
        Bukkit.broadcastMessage(colorize(message));
    }

    // ── Internal Utilities ─────────────────────────────────────────────────────

    /**
     * Utility method to dry up your code and ensure placeholder replacement uniformity.
     */
    private String applyPlaceholders(String base, Player player, String rankDisplay) {
        if (base == null) return "";
        return base.replace("%player%", player.getName())
                   .replace("%rank%", rankDisplay);
    }

    private String colorize(String msg) {
        return msg == null ? "" : msg.replace("&", "§");
    }
}
