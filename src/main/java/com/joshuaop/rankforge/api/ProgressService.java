package com.joshuaop.rankforge.api;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.entity.Player;

/**
 * Calculates a player's progress percentage toward their next rank.
 * Uses SoftDependency for Vault balance — no hook system required.
 */
public class ProgressService {

    private final RankForge plugin;

    public ProgressService(RankForge plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns progress 0.0–100.0 (minimum across all requirement types).
     */
    public double getPercent(Player player) {
        PlayerData data = loadData(player);
        String nextId   = plugin.getRankManager().getNextRankId(data.rankId());
        if (nextId == null || nextId.isBlank()) return 100.0;

        RankModel next = plugin.getRankManager().getRankData(nextId);
        if (next == null) return 100.0;

        double money   = plugin.getSoftDependency().getBalance(player);
        double moneyPct = next.getRequiredMoney() > 0
                ? Math.min(100.0, (money / next.getRequiredMoney()) * 100.0) : 100.0;
        double xpPct    = next.getRequiredXpLevel() > 0
                ? Math.min(100.0, ((double) player.getLevel() / next.getRequiredXpLevel()) * 100.0) : 100.0;

        return Math.min(moneyPct, xpPct);
    }

    /** Returns a 10-block colored progress bar. */
    public String getProgressBar(Player player) {
        double pct    = getPercent(player);
        int    filled = (int) (pct / 10.0);
        var    sb     = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i == filled) sb.append("§7");
            sb.append("█");
        }
        return sb.toString();
    }

    private PlayerData loadData(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        return cache.contains(player.getUniqueId())
                ? cache.get(player.getUniqueId())
                : PlayerData.defaultData(player.getUniqueId(), player.getName(),
                        plugin.getRankManager().getDefaultRankId());
    }
}
