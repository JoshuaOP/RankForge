package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents GUI click spam by enforcing a per-player cooldown.
 * Much shorter cooldown than AntiBypassManager (default 400 ms).
 */
public class GuiClickShieldManager {

    private final RankForge plugin;
    private final ConcurrentHashMap<UUID, Long> timestamps = new ConcurrentHashMap<>();
    private long cooldownMs;

    public GuiClickShieldManager(RankForge plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Returns true if the click is allowed, false if too fast.
     * Updates the timestamp on each allowed click.
     */
    public boolean allow(UUID uuid) {
        if (!plugin.getConfig().getBoolean("gui-click-shield.enabled", true)) return true;
        long now  = System.currentTimeMillis();
        Long last = timestamps.get(uuid);
        if (last != null && (now - last) < cooldownMs) return false;
        timestamps.put(uuid, now);
        return true;
    }

    /** Remaining cooldown in milliseconds, or 0. */
    public long remaining(UUID uuid) {
        Long last = timestamps.get(uuid);
        if (last == null) return 0L;
        return Math.max(0L, cooldownMs - (System.currentTimeMillis() - last));
    }

    public void remove(UUID uuid) {
        timestamps.remove(uuid);
    }

    public void reload() {
        cooldownMs = plugin.getConfig().getLong("gui-click-shield.cooldown-ms", 400L);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("gui-click-shield.enabled", true);
    }
}
