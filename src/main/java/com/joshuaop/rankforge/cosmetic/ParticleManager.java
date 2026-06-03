package com.joshuaop.rankforge.cosmetic;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.performance.PerformanceMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rank-based particle trail system.
 *
 * Handles timed rankup trails that trigger exclusively on rank-up.
 * Login restoration particles have been completely disabled.
 *
 * Performance-aware: reduces or disables particles when TPS is low.
 * Thread-safe: all active task state is stored in ConcurrentHashMaps.
 */
public class ParticleManager {

    private final RankForge             plugin;

    /** Active particle tasks per player UUID. */
    private final Map<UUID, BukkitTask> activeTasks  = new ConcurrentHashMap<>();

    /**
     * Stores the maximum run iterations allowed for a player's active rankup trail.
     */
    private final Map<UUID, Integer>    maxIterations = new ConcurrentHashMap<>();

    public ParticleManager(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start a timed rankup particle trail.
     * Automatically stops after {@code cosmetic.particles.rankup-trail-duration-seconds} seconds.
     */
    public void startRankupTrail(Player player, String rankId) {
        if (!isEnabled()) return;
        stopTrail(player);

        long durationSec = plugin.getConfig().getLong(
                "cosmetic.particles.rankup-trail-duration-seconds", 30L);
        if (durationSec <= 0) return; // configured off

        // The task runs every 4 ticks (see runTaskTimer). 20 ticks = 1 second.
        // Therefore, iterations per second = 20 / 4 = 5 runs per second.
        int totalIterations = (int) (durationSec * 5);
        maxIterations.put(player.getUniqueId(), totalIterations);

        startTrailInternal(player, rankId);
    }

    /** Stop the particle trail for a player and clean up all associated state. */
    public void stopTrail(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        BukkitTask task = activeTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        maxIterations.remove(uuid);
    }

    /** Stop all active trails. Called on plugin shutdown or reload. */
    public void stopAll() {
        activeTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        activeTasks.clear();
        maxIterations.clear();
    }

    /** @return the number of players currently with an active trail. */
    public int getActiveCount() { return activeTasks.size(); }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void startTrailInternal(Player player, String rankId) {
        String particleName = plugin.getConfig()
                .getString("cosmetic.particles.ranks." + rankId, "CRIT");

        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            particle = Particle.CRIT;
            if (plugin.isDebug())
                plugin.getLogger().warning("[Particles] Unknown particle '" + particleName
                        + "' for rank '" + rankId + "' — defaulting to CRIT.");
        }

        final UUID     uuid          = player.getUniqueId();
        final Particle finalParticle = particle;

        BukkitRunnable runnableTask = new BukkitRunnable() {
            private int elapsedRuns = 0;

            @Override
            public void run() {
                // Safety 1: cancel if player went offline
                if (!player.isOnline()) {
                    cleanupState(getTaskId());
                    cancel();
                    return;
                }

                // Safety 2: Check iteration restrictions for timed (rankup) trails
                Integer maxAllowed = maxIterations.get(uuid);
                if (maxAllowed == null) {
                    cleanupState(getTaskId());
                    cancel();
                    return;
                }

                if (elapsedRuns >= maxAllowed) {
                    cleanupState(getTaskId());
                    cancel();
                    return;
                }

                elapsedRuns++;

                // Performance gate
                if (!isParticlesAllowed()) return;

                spawnTrail(player, finalParticle);
            }

            /**
             * Only removes state maps if this exact task run thread context owns the registration!
             */
            private void cleanupState(int operatingTaskId) {
                activeTasks.computeIfPresent(uuid, (key, currentTask) -> {
                    if (currentTask.getTaskId() == operatingTaskId) {
                        maxIterations.remove(uuid);
                        return null; // Removes from activeTasks map safely
                    }
                    return currentTask; // Keeps the newer task override intact!
                });
            }
        };

        // Fire and store task mapping
        BukkitTask assignedTask = runnableTask.runTaskTimer(plugin, 0L, 4L);
        activeTasks.put(uuid, assignedTask);
    }

    private void spawnTrail(Player player, Particle particle) {
        if (player.getWorld() == null) return;
        Location loc   = player.getLocation().add(0, 0.1, 0);
        int      count = plugin.getPerformanceManager().getMode() == PerformanceMode.HIGH ? 3 : 1;
        try {
            player.getWorld().spawnParticle(particle, loc, count, 0.2, 0.0, 0.2, 0);
        } catch (Exception e) {
            if (plugin.isDebug())
                plugin.getLogger().warning("[Particles] Failed to spawn particle: " + e.getMessage());
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("cosmetic.particles.enabled", true);
    }

    private boolean isParticlesAllowed() {
        return plugin.getPerformanceManager().getMode() != PerformanceMode.LOW;
    }
}
