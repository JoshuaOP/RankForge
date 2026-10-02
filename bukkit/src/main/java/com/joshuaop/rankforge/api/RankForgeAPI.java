package com.joshuaop.rankforge.api;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.expansion.ExpansionRegistry;
import com.joshuaop.rankforge.api.gui.ExternalGUIRegistry;
import com.joshuaop.rankforge.api.hook.HookRegistry;
import com.joshuaop.rankforge.api.requirement.CustomRequirementRegistry;
import com.joshuaop.rankforge.experience.ExperienceManager;
import com.joshuaop.rankforge.experience.RankHistoryManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Public API for external plugins to interact with RankForge.
 *
 * <h3>Quick start:</h3>
 * <pre>{@code
 * RankForgeAPI api = RankForgeAPI.getInstance();
 * if (api == null) return;
 *
 * api.rankUp(player).thenAccept(success -> { ... });
 * api.setRank(player, "VIP").thenAccept(success -> { ... });
 * api.resetRank(player).thenAccept(success -> { ... });
 * PlayerRank rank = api.getPlayerRank(player);
 * }</pre>
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

    public static RankForgeAPI getInstance() { return instance; }

    public PlayerRank getPlayerRank(Player player) {
        return rankService.getPlayerRank(player);
    }

    public CompletableFuture<Boolean> rankUp(Player player) {
        boolean success = rankService.rankUp(player);
        return CompletableFuture.completedFuture(success);
    }

    public CompletableFuture<Boolean> setRank(Player player, String rankId) {
        boolean success = rankService.setRank(player, rankId);
        return CompletableFuture.completedFuture(success);
    }

    public CompletableFuture<Boolean> setRank(Player player, String rankId, CommandSender setter) {
        CompletableFuture<Boolean> future = rankService.setRank(player, rankId, setter);
        return future != null ? future : CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> resetRank(Player player) {
        rankService.resetRank(player);
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> resetRank(Player player, CommandSender setter) {
        CompletableFuture<Void> future = rankService.resetRank(player, setter);
        if (future == null) {
            return CompletableFuture.completedFuture(true);
        }
        return future.thenApply(v -> true).exceptionally(ex -> false);
    }

    public double getProgress(Player player) {
        return progressService.getPercent(player);
    }

    /**
     * Cleans a player's name by stripping away the Geyser/Floodgate prefix 
     * if configured to do so.
     *
     * @param player The player whose name needs checking.
     * @return The stripped name if the player is from Bedrock, or their default standard name.
     */
    public String getCleanName(Player player) {
        if (player == null) return "";
        String rawName = player.getName();
        
        // Read prefix directly from crossplay config settings
        String prefix = plugin.getConfig().getString("crossplay.bedrock-prefix", ".");
        
        if (prefix != null && !prefix.isBlank() && rawName.startsWith(prefix)) {
            return rawName.substring(prefix.length());
        }
        
        return rawName;
    }

    public ExperienceManager         getExperienceManager()         { return plugin.getExperienceManager(); }
    public RankHistoryManager        getHistoryManager()            { return plugin.getHistoryManager(); }
    public CustomRequirementRegistry getCustomRequirementRegistry() { return plugin.getCustomRequirementRegistry(); }
    public ExpansionRegistry         getExpansionRegistry()         { return plugin.getExpansionRegistry(); }
    public HookRegistry              getHookRegistry()              { return plugin.getHookRegistry(); }
    public ExternalGUIRegistry       getExternalGUIRegistry()       { return plugin.getExternalGUIRegistry(); }
    public RankService               getRankService()               { return rankService; }
    public ProgressService           getProgressService()           { return progressService; }
}
