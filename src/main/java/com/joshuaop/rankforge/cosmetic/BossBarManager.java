package com.joshuaop.rankforge.cosmetic;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Displays rank-themed boss bars on rank-up and progression stages.
 * Each bar auto-dismisses after a configurable duration.
 */
public class BossBarManager {

    private final RankForge              plugin;
    private final Map<UUID, BossBar>     activeBars = new ConcurrentHashMap<>();

    public BossBarManager(RankForge plugin) {
        this.plugin = plugin;
    }

    /** Show a rank-up congratulation boss bar. */
    public void showRankupBar(Player player, String rankDisplay) {
        if (!plugin.getConfig().getBoolean("cosmetic.bossbar.enabled", true)) return;
        showBar(player, "§6✦ Ranked up to " + rankDisplay + " §6✦", BarColor.YELLOW, BarStyle.SOLID, 1.0);
    }

    /** Show a progress boss bar (fills proportionally to progress %). */
    public void showProgressBar(Player player, double progress) {
        if (!plugin.getConfig().getBoolean("cosmetic.bossbar.enabled", true)) return;
        String label = "§7Progress to next rank: §e" + String.format("%.1f", progress) + "§7%";
        showBar(player, label, BarColor.GREEN, BarStyle.SEGMENTED_10, Math.min(1.0, progress / 100.0));
    }

    private void showBar(Player player, String title, BarColor color, BarStyle style, double progress) {
        removeBar(player);
        BossBar bar = Bukkit.createBossBar(title, color, style);
        bar.setProgress(progress);
        bar.addPlayer(player);
        activeBars.put(player.getUniqueId(), bar);

        int displayTicks = plugin.getConfig().getInt("cosmetic.bossbar.duration-ticks", 100);
        new BukkitRunnable() {
            @Override public void run() { removeBar(player); }
        }.runTaskLater(plugin, displayTicks);
    }

    public void removeBar(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) { bar.removeAll(); }
    }

    public void removeAll() {
        activeBars.values().forEach(BossBar::removeAll);
        activeBars.clear();
    }
}
