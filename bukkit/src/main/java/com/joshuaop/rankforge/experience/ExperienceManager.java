package com.joshuaop.rankforge.experience;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.gui.PlayerDataEditorGUI;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages RankForge's leveling system using real vanilla Minecraft levels.
 * Bound to the /ranks xp administration command pipeline.
 *
 * <p>Levels are read from and written directly to the player's vanilla experience track.
 * This manager provides helper methods to grant, deduct, and query levels while safeguarding
 * fractional progress bar percentages.
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
     * Award vanilla levels to an online player, maintaining fractional progress bar data.
     */
    public void award(Player player, long amount) {
        if (amount <= 0) return;
        
        int currentLevel = player.getLevel();
        int targetLevel = (int) Math.min(Integer.MAX_VALUE, currentLevel + amount);
        
        // Retain original fractional exp percentage bar layer
        float currentExpProgress = player.getExp(); 
        
        player.setLevel(targetLevel);
        player.setExp(currentExpProgress);
        
        // Sync administrative menus visually
        syncDataAndRefreshGUIs(player);
    }

    /**
     * Deduct the XP level cost defined on the target rank's RankModel.
     * Reads the requirement directly from the loaded rank data so it always
     * reflects the current ranks.yml, regardless of config reload order.
     *
     * @param player     The player ranking up
     * @param nextRankId The rank ID they are moving into (e.g., "Member", "Builder")
     */
    public void deductRankup(Player player, String nextRankId) {
        RankModel model = plugin.getRankManager().getRank(nextRankId);
        if (model == null) return;

        int levelCost = model.getRequiredXpLevel();
        if (levelCost > 0) {
            deduct(player, levelCost);
        }
    }

    /**
     * Deduct vanilla levels from an online player (floored at level 0).
     */
    public void deduct(Player player, long amount) {
        if (amount <= 0) return;
        
        int currentLevel = player.getLevel();
        int targetLevel = Math.max(0, currentLevel - (int) amount);
        
        float currentExpProgress = player.getExp();
        
        player.setLevel(targetLevel);
        player.setExp(currentExpProgress);
        
        syncDataAndRefreshGUIs(player);
    }

    /**
     * Set vanilla level directly while protecting fractional progress via /ranks xp set.
     */
    public void set(Player player, long amount) {
        int targetLevel = Math.max(0, (int) amount);
        float currentExpProgress = player.getExp();
        
        player.setLevel(targetLevel);
        player.setExp(currentExpProgress);
        
        syncDataAndRefreshGUIs(player);
    }

    // ── Query (Maintains getXp name hooks to keep /ranks xp queries working) ───

    /** 
     * Returns the player's current Level. 
     * Falls back to Cache/PlayerData calculations if the player is offline.
     */
    public long getXp(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            return player.getLevel();
        }
        PlayerData d = cache.getRaw(uuid);
        if (d != null) {
            // Compute the level matching the raw total XP points in storage fallback
            return getLevelFromTotalXp((int) d.experience());
        }
        return 0L;
    }

    /** Returns the player's current vanilla level. */
    public long getXp(Player player) {
        return player.getLevel();
    }

    /**
     * Formats the output text returned by the /ranks xp command to explicitly show the Level format.
     * Output format: "§aLevel 80"
     */
    public String getFormattedXp(Player player) {
        return "§aLevel " + player.getLevel();
    }

    // ── Vanilla Math Calculations ─────────────────────────────────────────────

    /**
     * Accurately calculates a player's total experience points from their current 
     * level progress bar and overall level stage (needed for accurate offline caching).
     */
    private int getVanillaTotalXp(Player player) {
        int level = player.getLevel();
        int xpForLevel = getXpNeededToReachLevel(level);
        int xpProgress = Math.round(player.getExp() * player.getExpToLevel());
        return xpForLevel + xpProgress;
    }

    /**
     * Back-calculates vanilla level number from raw total XP points.
     */
    private int getLevelFromTotalXp(int totalXp) {
        int level = 0;
        while (getXpNeededToReachLevel(level + 1) <= totalXp) {
            level++;
        }
        return level;
    }

    /**
     * Evaluates total accumulated XP needed to reach a specific level via non-linear progression steps.
     */
    private int getXpNeededToReachLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
    }

    // ── Helpers & UI Synchronizers ────────────────────────────────────────────

    /**
     * Keeps your cache layer updated with the exact vanilla experience values 
     * and forces immediate administrative GUI redraws.
     */
    private void syncDataAndRefreshGUIs(Player target) {
        UUID uuid = target.getUniqueId();
        PlayerData data = cache.getRaw(uuid);
        if (data == null) {
            data = plugin.getRankManager().getRepository()
                    .loadOrCreate(uuid, target.getName());
        }

        PlayerData updated = cache.update(uuid,
                current -> current.withExperience(getVanillaTotalXp(target)));
        if (updated == null) {
            updated = data.withExperience(getVanillaTotalXp(target));
            cache.put(uuid, updated);
        }

        for (Player onlineAdmin : Bukkit.getOnlinePlayers()) {
            String title = onlineAdmin.getOpenInventory().getTitle();
            
            if (title != null && title.startsWith(plugin.getGuiConfig().playerDataEditorTitlePrefix()) && title.endsWith(target.getName())) {
                if (PlayerDataEditorGUI.isOpen(onlineAdmin.getUniqueId())) {
                    new PlayerDataEditorGUI(plugin).open(onlineAdmin, target.getUniqueId(), target.getName());
                }
            }
        }
    }
}
