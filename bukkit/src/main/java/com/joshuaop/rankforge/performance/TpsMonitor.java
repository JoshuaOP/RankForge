package com.joshuaop.rankforge.performance;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Measures server TPS by tracking elapsed time between scheduled ticks.
 * Runs every 100 ticks and maintains a rolling average.
 */
public class TpsMonitor extends BukkitRunnable {

    private static final int   SAMPLE_SIZE    = 10;
    private static final long  SAMPLE_TICKS   = 100L;
    private static final double IDEAL_MS      = SAMPLE_TICKS * 50.0;

    private final double[] samples = new double[SAMPLE_SIZE];
    private int   sampleIndex = 0;
    private long  lastTime    = System.currentTimeMillis();
    private double currentTps = 20.0;

    private final RankForge plugin;

    public TpsMonitor(RankForge plugin) {
        this.plugin = plugin;
        for (int i = 0; i < SAMPLE_SIZE; i++) samples[i] = 20.0;
    }

    public void start() {
        runTaskTimer(plugin, SAMPLE_TICKS, SAMPLE_TICKS);
    }

    @Override
    public void run() {
        long now     = System.currentTimeMillis();
        long elapsed = now - lastTime;
        lastTime     = now;

        double tps  = Math.min(20.0, IDEAL_MS / Math.max(1, elapsed) * 20.0);
        samples[sampleIndex % SAMPLE_SIZE] = tps;
        sampleIndex++;

        double sum = 0;
        for (double s : samples) sum += s;
        currentTps = sum / SAMPLE_SIZE;
    }

    /** @return rolling average TPS (capped at 20.0). */
    public double getTps() { return currentTps; }

    /** @return true if TPS is below 14 (significant lag). */
    public boolean isLagging() { return currentTps < 14.0; }
}
