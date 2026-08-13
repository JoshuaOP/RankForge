package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;

/**
 * Tracks the plugin version and logs it on startup.
 */
public class VersionManager {

    private final RankForge plugin;
    private final String    version;

    public VersionManager(RankForge plugin) {
        this.plugin  = plugin;
        this.version = plugin.getDescription().getVersion();
    }

    public void logVersion() {
        plugin.getLogger().info("Running RankForge v" + version);
    }

    public String getVersion() {
        return version;
    }

    public String getFormattedVersion() {
        return "§6RankForge §7v§a" + version;
    }
}
