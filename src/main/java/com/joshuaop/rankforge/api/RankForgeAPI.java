package com.joshuaop.rankforge.api;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.expansion.ExpansionRegistry;
import com.joshuaop.rankforge.api.gui.ExternalGUIRegistry;
import com.joshuaop.rankforge.api.hook.HookRegistry;
import com.joshuaop.rankforge.api.requirement.CustomRequirementRegistry;
import com.joshuaop.rankforge.api.reward.CustomRewardRegistry;
import com.joshuaop.rankforge.challenge.RankChallengeManager;
import com.joshuaop.rankforge.experience.ExperienceManager;
import com.joshuaop.rankforge.experience.LeaderboardManager;
import com.joshuaop.rankforge.experience.RankHistoryManager;
import com.joshuaop.rankforge.manager.GitHubUpdateNotifier;
import com.joshuaop.rankforge.quest.RankQuestManager;
import com.joshuaop.rankforge.reward.DailyRewardManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Public API for external plugins to interact with RankForge.
 *
 * <h3>Quick start:</h3>
 * <pre>{@code
 * RankForgeAPI api = RankForgeAPI.getInstance();
 * if (api == null) return; // RankForge not loaded
 *
 * // Rank operations
 * api.rankUp(player);
 * api.setRank(player, "VIP");
 * api.resetRank(player);
 * PlayerRank rank = api.getPlayerRank(player);
 *
 * // Experience
 * api.getExperienceManager().award(player, 500);
 *
 * // Leaderboard
 * api.getLeaderboardManager().getTop(10);
 *
 * // Register custom requirement
 * api.getCustomRequirementRegistry().register("kills", new KillCountRequirement());
 *
 * // Register expansion
 * api.getExpansionRegistry().register(new MyExpansion(api));
 * }</pre>
 *
 * <p>All sub-APIs are null-safe; check {@link #getInstance()} before use.
 */
public class RankForgeAPI {

    private static RankForgeAPI    instance;
    private final  RankForge       plugin;
    private final  RankService     rankService;
    private final  ProgressService progressService;

    public RankForgeAPI(RankForge plugin) {
        this.plugin          = plugin;
        this.progressService = new ProgressService(plugin);
        this.rankService     = new RankService(plugin, progressService);
        instance             = this;
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    /**
     * @return the singleton API instance, or {@code null} if RankForge is not loaded.
     */
    public static RankForgeAPI getInstance() { return instance; }

    // ── Core rank operations ──────────────────────────────────────────────────

    /**
     * Get a snapshot of the player's current rank state.
     *
     * @return {@link PlayerRank} record, or {@code null} if data is not loaded yet.
     */
    public PlayerRank getPlayerRank(Player player) {
        return rankService.getPlayerRank(player);
    }

    /**
     * Attempt to rank up the player, enforcing requirements.
     * Fires {@link com.joshuaop.rankforge.api.event.RankupEvent} (cancellable).
     *
     * @return true if the player ranked up successfully.
     */
    public boolean rankUp(Player player) {
        return rankService.rankUp(player);
    }

    /**
     * Directly set a player's rank by ID (bypasses requirements).
     * Fires {@link com.joshuaop.rankforge.api.event.RankSetEvent} (cancellable).
     *
     * @return true if the rank was applied.
     */
    public boolean setRank(Player player, String rankId) {
        return rankService.setRank(player, rankId);
    }

    /**
     * Set a player's rank with a specific command sender for audit logging.
     * Fires {@link com.joshuaop.rankforge.api.event.RankSetEvent} (cancellable).
     */
    public boolean setRank(Player player, String rankId, CommandSender setter) {
        return rankService.setRank(player, rankId, setter);
    }

    /**
     * Reset a player's rank to the server default.
     * Fires {@link com.joshuaop.rankforge.api.event.RankResetEvent} (cancellable).
     */
    public void resetRank(Player player) {
        rankService.resetRank(player);
    }

    /**
     * Reset a player's rank with an explicit command sender for event/audit data.
     * Fires {@link com.joshuaop.rankforge.api.event.RankResetEvent} (cancellable).
     */
    public void resetRank(Player player, CommandSender setter) {
        rankService.resetRank(player, setter);
    }

    /**
     * Get the player's current progress toward their next rank (0.0–100.0).
     */
    public double getProgress(Player player) {
        return progressService.getPercent(player);
    }

    // ── Sub-systems ───────────────────────────────────────────────────────────

    /** Experience (XP) system. Award, deduct, or query player XP. */
    public ExperienceManager         getExperienceManager()             { return plugin.getExperienceManager(); }

    /** Rank history log. Query per-player rank change history. */
    public RankHistoryManager        getHistoryManager()                { return plugin.getHistoryManager(); }

    /** Leaderboard system. Get top-N players by rank or XP. */
    public LeaderboardManager        getLeaderboardManager()            { return plugin.getLeaderboardManager(); }

    /** Daily reward system. Check cooldowns, grant rewards. */
    public DailyRewardManager        getDailyRewardManager()            { return plugin.getDailyRewardManager(); }

    /** Challenge system. Track and manage in-progress challenges. */
    public RankChallengeManager      getChallengeManager()              { return plugin.getChallengeManager(); }

    /** Quest system. Track and manage in-progress quests. */
    public RankQuestManager          getQuestManager()                  { return plugin.getQuestManager(); }

    // ── Developer API registry singletons ─────────────────────────────────────

    /** Register custom rank requirements (third-party plugins). */
    public CustomRequirementRegistry getCustomRequirementRegistry()     { return plugin.getCustomRequirementRegistry(); }

    /** Register custom rank-up rewards (third-party plugins). */
    public CustomRewardRegistry      getCustomRewardRegistry()          { return plugin.getCustomRewardRegistry(); }

    /** Register and manage RankForge expansion modules. */
    public ExpansionRegistry         getExpansionRegistry()             { return plugin.getExpansionRegistry(); }

    /** Register lightweight plugin hooks for rank-change callbacks. */
    public HookRegistry              getHookRegistry()                  { return plugin.getHookRegistry(); }

    /** Override built-in GUIs with custom inventory providers. */
    public ExternalGUIRegistry       getExternalGUIRegistry()           { return plugin.getExternalGUIRegistry(); }

    /** GitHub update checker — query latest version status. */
    public GitHubUpdateNotifier      getUpdateNotifier()                { return plugin.getUpdateNotifier(); }

    // ── Advanced service access ───────────────────────────────────────────────

    /** @return the underlying {@link RankService} for advanced usage. */
    public RankService     getRankService()     { return rankService; }

    /** @return the underlying {@link ProgressService} for advanced usage. */
    public ProgressService getProgressService() { return progressService; }
}
