package com.joshuaop.rankforge.experience;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.PlayerData;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages RankForge's internal experience (XP) system.
 *
 * <p>XP is stored directly in {@link PlayerData#experience()}. This manager
 * provides helper methods to grant, deduct, and query XP, as well as computing
 * display strings used in commands and GUIs.
 *
 * <p>XP is awarded automatically on rank-up (amount configurable per rank or globally
 * in config.yml under {@code experience.rankup-xp}).
 */
public class ExperienceManager {

    private final RankForge    plugin;
    private final CacheManager cache;

    public ExperienceManager(RankForge plugin) {
        this.plugin = plugin;
        this.cache  = plugin.getRankManager().getCacheManager();
    }

    // ── Award / Deduct ────────────────────────────────────────────────────────

    /**
     * Award XP to a player. Amount is taken from config unless overridden.
     * Thread-safe: updates the in-memory cache; disk persistence handled by sync service.
     */
    public void award(Player player, long amount) {
        if (amount <= 0) return;
        UUID uuid = player.getUniqueId();
        PlayerData data = getOrDefault(uuid, player.getName());
        PlayerData updated = data.withExperience(data.experience() + amount);
        cache.put(uuid, updated);
    }

    /**
     * Award the configured rank-up XP to the player.
     * Reads {@code experience.rankup-xp} from config.yml (default 100).
     */
    public void awardRankup(Player player) {
        if (!plugin.getConfig().getBoolean("experience.enabled", true)) return;
        long xp = plugin.getConfig().getLong("experience.rankup-xp", 100L);
        award(player, xp);
    }

    /**
     * Deduct XP from a player (floored at 0).
     */
    public void deduct(Player player, long amount) {
        if (amount <= 0) return;
        UUID uuid = player.getUniqueId();
        PlayerData data = getOrDefault(uuid, player.getName());
        long newXp = Math.max(0L, data.experience() - amount);
        cache.put(uuid, data.withExperience(newXp));
    }

    /**
     * Set XP directly.
     */
    public void set(Player player, long amount) {
        UUID uuid = player.getUniqueId();
        PlayerData data = getOrDefault(uuid, player.getName());
        cache.put(uuid, data.withExperience(Math.max(0L, amount)));
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** Returns the player's current XP (0 if not cached). */
    public long getXp(UUID uuid) {
        PlayerData d = cache.get(uuid);
        return d != null ? d.experience() : 0L;
    }

    /** Returns the player's current XP (0 if not cached). */
    public long getXp(Player player) {
        return getXp(player.getUniqueId());
    }

    /**
     * Returns a formatted XP string, e.g. {@code "§a1,234 XP"}.
     */
    public String getFormattedXp(Player player) {
        return "§a" + String.format("%,d", getXp(player)) + " §7XP";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlayerData getOrDefault(UUID uuid, String name) {
        PlayerData d = cache.get(uuid);
        return d != null ? d : PlayerData.defaultData(uuid, name,
                plugin.getRankManager().getDefaultRankId());
    }
}
