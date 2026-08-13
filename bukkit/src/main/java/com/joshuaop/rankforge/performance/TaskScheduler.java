package com.joshuaop.rankforge.performance;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized task registry — all plugin tasks are tracked here so they can
 * be cleanly cancelled on shutdown and monitored via /rank stats.
 */
public class TaskScheduler {

    private final RankForge                  plugin;
    private final Map<Integer, BukkitTask>   tasks  = new ConcurrentHashMap<>();
    private final AtomicInteger              idGen  = new AtomicInteger(0);

    public TaskScheduler(RankForge plugin) {
        this.plugin = plugin;
    }

    /** Schedule a repeating task (sync). Returns a tracking ID. */
    public int repeat(Runnable action, long delayTicks, long periodTicks) {
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() { action.run(); }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
        return register(task);
    }

    /** Schedule a repeating async task. Returns a tracking ID. */
    public int repeatAsync(Runnable action, long delayTicks, long periodTicks) {
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() { action.run(); }
        }.runTaskTimerAsynchronously(plugin, delayTicks, periodTicks);
        return register(task);
    }

    /** Run a task on the main thread after a delay. Cleans up after execution. */
    public void delayed(Runnable action, long delayTicks) {
        final int id = idGen.incrementAndGet();
        
        BukkitTask task = new BukkitRunnable() {
            @Override 
            public void run() { 
                try {
                    action.run(); 
                } finally {
                    tasks.remove(id); // Clean up tracking map instantly when finished
                }
            }
        }.runTaskLater(plugin, delayTicks);
        
        tasks.put(id, task);
    }

    /** Run a task asynchronously immediately. Tracks execution context. */
    public void async(Runnable action) {
        final int id = idGen.incrementAndGet();
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    action.run();
                } finally {
                    tasks.remove(id); // Clean up tracking map instantly when finished
                }
            }
        }.runTaskAsynchronously(plugin);
        
        tasks.put(id, task);
    }

    /** Cancel a specific tracked task. */
    public void cancel(int trackingId) {
        BukkitTask task = tasks.remove(trackingId);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    /** Cancel all tracked tasks — call on plugin shutdown. */
    public void cancelAll() {
        tasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        tasks.clear();
    }

    public int getActiveTaskCount() { return tasks.size(); }

    private int register(BukkitTask task) {
        int id = idGen.incrementAndGet();
        tasks.put(id, task);
        return id;
    }
}
