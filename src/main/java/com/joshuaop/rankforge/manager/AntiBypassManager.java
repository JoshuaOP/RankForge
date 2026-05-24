package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents spam attacks on rank-up and commands.
 * Tracks the last-action timestamp per player using a lock-free concurrent map.
 */
public class AntiBypassManager {

    private final RankForge plugin;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private long cooldownMs;

    public AntiBypassManager(RankForge plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Returns true if the player is allowed to act (not on cooldown). */
    public boolean check(UUID uuid) {
        if (!plugin.getConfig().getBoolean("anti-bypass.enabled", true)) return true;
        long now  = System.currentTimeMillis();
        Long last = cooldowns.get(uuid);
        if (last != null && (now - last) < cooldownMs) return false;
        cooldowns.put(uuid, now);
        return true;
    }

    /** Remaining cooldown milliseconds for the player, or 0 if none. */
    public long remaining(UUID uuid) {
        Long last = cooldowns.get(uuid);
        if (last == null) return 0L;
        long diff = cooldownMs - (System.currentTimeMillis() - last);
        return Math.max(0L, diff);
    }

    /** Remove a player from the cooldown map when they log out. */
    public void remove(UUID uuid) {
        cooldowns.remove(uuid);
    }

    /** Clear all cooldowns (e.g., on reload). */
    public void clear() {
        cooldowns.clear();
    }

    public void reload() {
        cooldownMs = plugin.getConfig().getLong("anti-bypass.cooldown-ms", 2000L);
    }

    public int getTrackedPlayers() {
        return cooldowns.size();
    }
}
