package com.joshuaop.rankforge.performance;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Monitors TPS and switches the server into an appropriate PerformanceMode.
 * Other systems (GUI, particles) query this before enabling heavy effects.
 */
public class PerformanceManager {

    private final RankForge    plugin;
    private final TpsMonitor   tpsMonitor;
    private PerformanceMode    currentMode = PerformanceMode.HIGH;
    private BukkitTask         evaluateTask;
    private boolean            started     = false;

    private static final double HIGH_TPS   = 18.0;
    private static final double MEDIUM_TPS = 14.0;

    public PerformanceManager(RankForge plugin) {
        this.plugin     = plugin;
        this.tpsMonitor = new TpsMonitor(plugin);
    }

    public void start() {
        tpsMonitor.start();

        evaluateTask = new BukkitRunnable() {
            @Override public void run() { evaluateMode(); }
        }.runTaskTimer(plugin, 200L, 200L);

        started = true;
    }

    public void stop() {
        if (!started) return;
        try {
            if (!tpsMonitor.isCancelled()) tpsMonitor.cancel();
        } catch (IllegalStateException ignored) {}
        try {
            if (evaluateTask != null && !evaluateTask.isCancelled()) evaluateTask.cancel();
        } catch (IllegalStateException ignored) {}
        started = false;
    }

    private void evaluateMode() {
        double tps  = tpsMonitor.getTps();
        PerformanceMode newMode;

        if (tps >= HIGH_TPS)        newMode = PerformanceMode.HIGH;
        else if (tps >= MEDIUM_TPS) newMode = PerformanceMode.MEDIUM;
        else                        newMode = PerformanceMode.LOW;

        if (newMode != currentMode) {
            plugin.getLogger().info(
                    "[Perf] Mode switched " + currentMode + " → " + newMode
                    + " (TPS: " + String.format("%.1f", tps) + ")");
            currentMode = newMode;
        }
    }

    public PerformanceMode getMode()   { return currentMode; }
    public TpsMonitor getTpsMonitor()  { return tpsMonitor; }
    public boolean isHighPerformance() { return currentMode == PerformanceMode.HIGH; }
    public boolean isLowPerformance()  { return currentMode == PerformanceMode.LOW; }
}
