package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents GUI click spam by enforcing an atomic per-player cooldown.
 * Optimized with automatic memory leak reclamation handlers.
 */
public class GuiClickShieldManager implements Listener {

    private final RankForge plugin;
    private final ConcurrentHashMap<UUID, Long> timestamps = new ConcurrentHashMap<>();
    private long cooldownMs;

    public GuiClickShieldManager(RankForge plugin) {
        this.plugin = plugin;
        reload();
        
        // Register this instance as a listener to handle automatic memory cleanup on quit
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Returns true if the click is allowed, false if too fast.
     * Uses atomic atomic-state mutations to ensure complete thread-safety.
     */
    public boolean allow(UUID uuid) {
        if (uuid == null) return true;
        if (!isEnabled()) return true;

        long now = System.currentTimeMillis();

        // Atomic check-and-set wrapper block preventing parallel bypass windows
        Long previousValue = timestamps.compute(uuid, (key, last) -> {
            if (last != null && (now - last) < cooldownMs) {
                return last; // Keep the old timestamp, reject mutation
            }
            return now; // Cooldown expired or entry new, update to present window
        });

        // If the map value returned equals 'now', our update was successful and allowed
        return previousValue == now;
    }

    /** Remaining cooldown in milliseconds, or 0. */
    public long remaining(UUID uuid) {
        if (uuid == null) return 0L;
        Long last = timestamps.get(uuid);
        if (last == null) return 0L;
        return Math.max(0L, cooldownMs - (System.currentTimeMillis() - last));
    }

    public void remove(UUID uuid) {
        if (uuid != null) timestamps.remove(uuid);
    }

    public void reload() {
        cooldownMs = plugin.getGuiConfig().clickShieldCooldownMs();
    }

    public boolean isEnabled() {
        return plugin.getGuiConfig().clickShieldEnabled();
    }

    // ── Automated Memory Recovery ──────────────────────────────────────────────

    /**
     * BUG FIX: Listens for player disconnects to clean up data footprints.
     * Prevents internal RAM consumption degradation over extended server uptimes.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }
}
