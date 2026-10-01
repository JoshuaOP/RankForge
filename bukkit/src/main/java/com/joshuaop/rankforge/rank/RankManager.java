package com.joshuaop.rankforge.rank;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.DatabaseManager;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.db.RankDataRepository;
import com.joshuaop.rankforge.db.YamlPlayerDataStorage;

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

    /**
     * Loads and indexes all ranks from the rank YAML configuration.
     */
    public void loadRanks() {
        synchronized (lock) {
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

    /**
     * Flushes all cached player data during server shutdown.
     *
     * <p>Uses {@link DatabaseManager#isReadyForReads()} instead of {@code isConnected()}
     * to prevent writing to a database that is undergoing active recovery or broken.
     * If MySQL is unavailable or recovering, all cached records fall back directly to
     * {@link YamlPlayerDataStorage}.
     */
    public void flushNow() {
        plugin.getLogger().info("[RankForge] Flushing all cached player data for server shutdown...");

        Collection<PlayerData> snapshots = cacheManager.snapshotOnlineAndUnexpired();
        DatabaseManager dbManager = plugin.getDatabaseManager();
        YamlPlayerDataStorage yamlStorage = plugin.getYamlPlayerDataStorage();

        boolean safeForMySQL = dbManager != null && dbManager.isReadyForReads();

        if (safeForMySQL) {
            plugin.getLogger().info("[RankForge] Flushing " + snapshots.size() + " player records to MySQL...");
            int successCount = 0;

            for (PlayerData data : snapshots) {
                if (repository.save(data)) {
                    successCount++;
                } else if (yamlStorage != null) {
                    yamlStorage.savePlayerForEmergencyFallback(data);
                }
            }
            plugin.getLogger().info("[RankForge] Successfully saved " + successCount + "/" + snapshots.size() + " records to MySQL.");
        } else {
            plugin.getLogger().warning("[RankForge] MySQL is not ready during shutdown (recovering or disconnected). "
                    + "Flushing all " + snapshots.size() + " player records directly to YAML storage.");

            if (yamlStorage != null) {
                int yamlCount = 0;
                for (PlayerData data : snapshots) {
                    if (yamlStorage.savePlayerForEmergencyFallback(data)) {
                        yamlCount++;
                    }
                }
                plugin.getLogger().info("[RankForge] Emergency shutdown save complete: " + yamlCount + "/" + snapshots.size() + " saved to YAML.");
            } else {
                plugin.getLogger().severe("[RankForge] CRITICAL: YamlPlayerDataStorage is uninitialized during shutdown! Data loss prevention failed.");
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

    public Collection<RankModel> getModelList() {
        synchronized (lock) {
            return new ArrayList<>(ranks.values());
        }
    }

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
                    this.defaultRankId = fallback;
                }
            }
        }
        if (fallback == null) return;

        final String effectiveFallback = fallback;
        
        cacheManager.repairOrphanedRankIds(rankId -> getRank(rankId) != null, effectiveFallback);

        if (plugin.isDebug()) {
            plugin.getLogger().info("Rank repair complete (fallback='" + effectiveFallback + "').");
        }
    }
}
