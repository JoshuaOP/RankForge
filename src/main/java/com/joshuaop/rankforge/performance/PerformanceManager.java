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

    // Configurable thresholds with internal default fallbacks
    private double highTpsThreshold   = 18.0;
    private double mediumTpsThreshold = 14.0;

    public PerformanceManager(RankForge plugin) {
        this.plugin     = plugin;
        this.tpsMonitor = new TpsMonitor(plugin);
    }

    public void start() {
        if (started) return;

        // Load dynamic performance profiles from config.yml
        this.highTpsThreshold = plugin.getConfig().getDouble(
                "performance.modes.high-tps-threshold", 18.0);
        this.mediumTpsThreshold = plugin.getConfig().getDouble(
                "performance.modes.medium-tps-threshold", 14.0);

        tpsMonitor.start();

        evaluateTask = new BukkitRunnable() {
            @Override public void run() { evaluateMode(); }
        }.runTaskTimer(plugin, 200L, 200L);

        started = true;
    }

    public void stop() {
        if (!started) return;
        
        // Safely stop the TpsMonitor loop execution context
        try {
            tpsMonitor.cancel();
        } catch (Exception ignored) {}
        
        // Safely cancel the mode evaluation scheduler loop
        if (evaluateTask != null) {
            try {
                evaluateTask.cancel();
            } catch (Exception ignored) {}
            evaluateTask = null;
        }
        
        started = false;
    }

    private void evaluateMode() {
        double tps  = tpsMonitor.getTps();
        PerformanceMode newMode;

        if (tps >= highTpsThreshold)        newMode = PerformanceMode.HIGH;
        else if (tps >= mediumTpsThreshold) newMode = PerformanceMode.MEDIUM;
        else                                newMode = PerformanceMode.LOW;

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
