package com.joshuaop.rankforge.cosmetic;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Displays rank-themed boss bars on rank-up and progression stages.
 * Each bar auto-dismisses after a configurable duration.
 *
 * Bug fix: the dismiss task is now tracked per-player and cancelled whenever
 * a new bar is shown or removeBar() is called. Previously, the runnable was
 * never cancelled which caused rapid rank-ups to silently remove the new bar
 * when the old timer expired.
 */
public class BossBarManager {

    private final RankForge          plugin;
    private final Map<UUID, BossBar> activeBars   = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> dismissTasks = new ConcurrentHashMap<>();

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
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() { removeBar(player); }
        }.runTaskLater(plugin, displayTicks);
        dismissTasks.put(player.getUniqueId(), task);
    }

    public void removeBar(Player player) {
        BukkitTask task = dismissTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) task.cancel();

        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    public void removeAll() {
        dismissTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        dismissTasks.clear();
        activeBars.values().forEach(BossBar::removeAll);
        activeBars.clear();
    }
}
