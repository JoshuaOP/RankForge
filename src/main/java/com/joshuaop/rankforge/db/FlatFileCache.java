package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Persists player data to plugins/RankForge/data/playerdata.yml.
 * This flat-file cache ensures rank data survives restarts even when MySQL is
 * unavailable.
 */
public class FlatFileCache {

    private final RankForge plugin;
    private final File file;

    public FlatFileCache(RankForge plugin) {
        this.plugin = plugin;
        
        // Define the data folder and file
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.file = new File(dataFolder, "playerdata.yml");
    }

    /**
     * Load all saved player records into the given CacheManager.
     */
    public void load(CacheManager cache) {
        if (!file.exists()) return;
        
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var section = cfg.getConfigurationSection("players");
        if (section == null) return;

        int count = 0;
        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String name = section.getString(uuidStr + ".name", "Unknown");
                String rank = section.getString(uuidStr + ".rank", plugin.getConfig().getString("ranks.default-rank", "Guest"));
                long exp = section.getLong(uuidStr + ".experience", 0L);
                double money = section.getDouble(uuidStr + ".money", 0.0);
                String lang = section.getString(uuidStr + ".language", "en");
                
                cache.put(uuid, new PlayerData(uuid, name, rank, exp, money, lang));
                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load player record for UUID: " + uuidStr);
            }
        }
        
        if (count > 0) {
            plugin.getLogger().info("Loaded " + count + " player record(s) from playerdata.yml.");
        }
    }

    /**
     * Save all current cache entries to data/playerdata.yml.
     */
    public void save(CacheManager cache) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (PlayerData data : cache.all()) {
            String base = "players." + data.uuid();
            cfg.set(base + ".name", data.playerName());
            cfg.set(base + ".rank", data.rankId());
            cfg.set(base + ".experience", data.experience());
            cfg.set(base + ".money", data.money());
            cfg.set(base + ".language", data.language());
        }
        
        try {
            // Ensure the data directory exists
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save playerdata.yml: " + e.getMessage());
        }
    }
}
