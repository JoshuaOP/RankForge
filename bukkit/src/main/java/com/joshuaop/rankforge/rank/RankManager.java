package com.joshuaop.rankforge.rank;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.RankDataRepository;

import java.util.*;

/**
 * Thread-safe in-memory index of all loaded ranks.
 * Owns the shared CacheManager and RankDataRepository used across the plugin.
 */
public class RankManager {

    private final RankForge          plugin;
    private final CacheManager       cacheManager;
    private final RankDataRepository repository;

    // Guard object for all read/write modifications to the ranks map
    private final Object lock = new Object();
    private LinkedHashMap<String, RankModel> ranks = new LinkedHashMap<>();
    private volatile String defaultRankId = "Guest";

    public RankManager(RankForge plugin) {
        this.plugin       = plugin;
        this.cacheManager = new CacheManager(plugin);
        this.repository   = new RankDataRepository(plugin, cacheManager);
    }

    public void loadRanks() {
        synchronized (lock) {
            // REVISION: Pulled configuration pulling inside the lock block to guarantee atomic lifecycle state
            if (plugin.getRankYamlManager() != null && plugin.getRankYamlManager().getConfig() != null) {
                String yamlDefault = plugin.getRankYamlManager().getConfig().getString("default-rank", "Guest");
                this.defaultRankId = (yamlDefault == null || yamlDefault.isBlank()) ? "Guest" : yamlDefault;
                
                LinkedHashMap<String, RankModel> fetchedRanks = plugin.getRankYamlManager().getRanks();
                this.ranks = fetchedRanks != null ? fetchedRanks : new LinkedHashMap<>();
            } else {
                this.defaultRankId = "Guest";
                this.ranks = new LinkedHashMap<>();
            }
            
            if (plugin.isDebug()) {
                plugin.getLogger().info("Indexed " + this.ranks.size() + " ranks.");
            }
        }
    }

    public void updateModel(RankModel model) {
        if (model == null || model.getId() == null || model.getId().isEmpty()) return;
        synchronized (lock) {
            ranks.put(model.getId(), model);
        }
    }

    public void removeModel(String rankId) {
        if (rankId == null || rankId.isEmpty()) return;
        synchronized (lock) {
            ranks.remove(rankId);
        }
    }

    /** Returns the RankModel for the given ID, or null if not found. */
    public RankModel getRank(String rankId) {
        if (rankId == null || rankId.isEmpty()) return null;
        synchronized (lock) {
            return ranks.get(rankId);
        }
    }

    public String getDefaultRankId() { 
        return defaultRankId; 
    }

    // REVISION: Returns an isolated snapshot array list to avoid ConcurrentModificationExceptions asynchronously
    public Collection<RankModel> getModelList() {
        synchronized (lock) {
            return new ArrayList<>(ranks.values());
        }
    }

    // REVISION: Returns an isolated snapshot hash set to avoid ConcurrentModificationExceptions asynchronously
    public Set<String> getRankIds() {
        synchronized (lock) {
            return new HashSet<>(ranks.keySet());
        }
    }

    public CacheManager getCacheManager() { 
        return cacheManager; 
    }

    public RankDataRepository getRepository() { 
        return repository; 
    }

    public RankModel getRankAtSlot(int slot) {
        synchronized (lock) {
            for (RankModel rank : ranks.values()) {
                // RankModel is non-null guaranteed by our updated builder framework
                if (rank.getSlot() == slot) {
                    return rank;
                }
            }
        }
        return null;
    }

    public String getNextRankId(String rankId) {
        if (rankId == null || rankId.isEmpty()) return "";
        synchronized (lock) {
            RankModel data = ranks.get(rankId);
            return data != null ? data.getNextRankId() : "";
        }
    }

    public String getDisplayName(String rankId) {
        if (rankId == null || rankId.isEmpty()) return "";
        synchronized (lock) {
            RankModel data = ranks.get(rankId);
            return data != null ? data.getDisplayName() : "§7" + rankId;
        }
    }

    public int getRankCount() {
        synchronized (lock) {
            return ranks.size();
        }
    }

    /**
     * Scans the player data cache and replaces any rank ID that no longer exists
     * in the current rank list with the configured default rank.
     *
     * <p>Call this after every ranks.yml reload to prevent orphaned rank references
     * from causing broken GUI displays, permission errors, or chain lookups.
     */
    public void repairOrphanedRanks() {
        String fallback = defaultRankId;

        if (getRank(fallback) == null) {
            synchronized (lock) {
                fallback = ranks.isEmpty() ? null : ranks.keySet().iterator().next();
                if (fallback != null) {
                    this.defaultRankId = fallback; // REVISION: Permanently heal misconfigured layouts
                }
            }
        }
        if (fallback == null) return;

        final String effectiveFallback = fallback;
        
        // REVISION: Ensure the condition check runs against the locked map safely via our accessor method
        cacheManager.repairOrphanedRankIds(rankId -> getRank(rankId) != null, effectiveFallback);

        if (plugin.isDebug()) {
            plugin.getLogger().info("Rank repair complete (fallback='" + effectiveFallback + "').");
        }
    }
}
