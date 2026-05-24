package com.joshuaop.rankforge.progress;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.ProgressService;
import org.bukkit.entity.Player;

/**
 * Thin façade over ProgressService, used internally across the plugin.
 * Delegates to the shared ProgressService from the API to avoid duplication.
 */
public class ProgressManager {

    private final RankForge plugin;

    public ProgressManager(RankForge plugin) {
        this.plugin = plugin;
    }

    private ProgressService getService() {
        return plugin.getApi().getProgressService();
    }

    /** Raw progress percentage (0.0 – 100.0). */
    public double getPercent(Player player) {
        return getService().getPercent(player);
    }

    /** 10-block coloured progress bar string. */
    public String getBar(Player player) {
        return getService().getProgressBar(player);
    }

    /** Bar + percentage, e.g. "§a██████§7████ §8(§e60.0%%§8)". */
    public String getFormatted(Player player) {
        double pct = getPercent(player);
        return getBar(player) + " §8(§e" + String.format("%.1f", pct) + "%%§8)";
    }
}
