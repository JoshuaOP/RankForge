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
 * Players receive a continuous particle trail based on their current rank.
 * Automatically reduces or disables particles when TPS is low.
 */
public class ParticleManager {

    private final RankForge              plugin;
    private final Map<UUID, BukkitTask>  activeTasks = new ConcurrentHashMap<>();

    public ParticleManager(RankForge plugin) {
        this.plugin = plugin;
    }

    /** Start a particle trail for the player based on their rank. */
    public void startTrail(Player player, String rankId) {
        if (!plugin.getConfig().getBoolean("cosmetic.particles.enabled", true)) return;
        stopTrail(player);

        String particleName = plugin.getConfig()
                .getString("cosmetic.particles.ranks." + rankId, "CRIT");
        Particle particle;
        try { particle = Particle.valueOf(particleName); }
        catch (IllegalArgumentException e) { particle = Particle.CRIT; }

        final Particle finalParticle = particle;
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (!isParticlesAllowed()) return;
                spawnTrail(player, finalParticle);
            }
        }.runTaskTimer(plugin, 0L, 4L);

        activeTasks.put(player.getUniqueId(), task);
    }

    /** Stop the particle trail for a player. */
    public void stopTrail(Player player) {
        BukkitTask task = activeTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) task.cancel();
    }

    public void stopAll() {
        activeTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        activeTasks.clear();
    }

    private void spawnTrail(Player player, Particle particle) {
        Location loc = player.getLocation().add(0, 0.1, 0);
        int count = plugin.getPerformanceManager().getMode() == PerformanceMode.HIGH ? 3 : 1;
        player.getWorld().spawnParticle(particle, loc, count, 0.2, 0.0, 0.2, 0);
    }

    private boolean isParticlesAllowed() {
        return plugin.getPerformanceManager().getMode() != PerformanceMode.LOW;
    }

    public int getActiveCount() { return activeTasks.size(); }
}
