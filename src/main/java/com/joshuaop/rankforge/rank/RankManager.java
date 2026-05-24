package com.joshuaop.rankforge.rank;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.RankDataRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * In-memory index of all loaded ranks.
 * Loads from RankYamlManager and exposes RankModel objects to the rest of the plugin.
 */
public class RankManager {

    private final RankForge          plugin;
    private final CacheManager       cacheManager;
    private final RankDataRepository repository;

    private LinkedHashMap<String, RankModel> ranks = new LinkedHashMap<>();
    private String defaultRankId;

    public RankManager(RankForge plugin) {
        this.plugin       = plugin;
        this.cacheManager = new CacheManager();
        this.repository   = new RankDataRepository(plugin, cacheManager);
    }

    /**
     * (Re)load all ranks from the YAML manager into memory.
     * Called on startup and whenever a hot-reload is triggered.
     */
    public void loadRanks() {
        defaultRankId = plugin.getConfig().getString("ranks.default-rank", "Guest");
        ranks = plugin.getRankYamlManager().getRanks();
        plugin.getLogger().info("[Ranks] Indexed " + ranks.size() + " ranks.");
    }

    // ── In-memory mutation (for DragDrop/Editor without reload) ──────────────

    /**
     * Update a single rank model in memory (called by DragDropRankEditorGUI).
     */
    public void updateModel(RankModel model) {
        ranks.put(model.getId(), model);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public RankModel getRankData(String rankId)      { return ranks.get(rankId); }
    public RankModel getRank(String rankId)          { return ranks.get(rankId); }
    public String    getDefaultRankId()              { return defaultRankId; }
    public Collection<RankModel> getModelList()      { return ranks.values(); }
    public Set<String> getRankIds()                  { return ranks.keySet(); }
    public CacheManager getCacheManager()            { return cacheManager; }
    public RankDataRepository getRepository()        { return repository; }

    /** Find the rank whose configured slot matches the given inventory slot. */
    public RankModel getRankAtSlot(int slot) {
        for (RankModel rank : ranks.values()) {
            if (rank.getSlot() == slot) return rank;
        }
        return null;
    }

    public String getNextRankId(String rankId) {
        RankModel data = ranks.get(rankId);
        return data != null ? data.getNextRankId() : "";
    }

    public String getDisplayName(String rankId) {
        RankModel data = ranks.get(rankId);
        return data != null ? data.getDisplayName() : "§7" + rankId;
    }

    public int getRankCount() { return ranks.size(); }
}
