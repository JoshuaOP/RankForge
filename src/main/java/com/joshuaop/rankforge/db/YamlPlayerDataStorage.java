package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * YAML-based player data storage.
 * Used as the default fallback when MySQL is unavailable.
 * Stores data at: plugins/RankForge/data/playerdata.yml
 *
 * File format:
 *   players:
 *     <uuid>:
 *       name: "PlayerName"
 *       rank: "Guest"
 *       experience: 0
 *       money: 0.0
 *       language: "en"
 */
public class YamlPlayerDataStorage {

    private final RankForge plugin;
    private final File      dataFile;
    private YamlConfiguration yaml;

    public YamlPlayerDataStorage(RankForge plugin) {
        this.plugin   = plugin;
        File dataDir  = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "playerdata.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().severe("[YamlStorage] Could not create playerdata.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(dataFile);
    }

    public PlayerData loadPlayer(UUID uuid, String playerName) {
        String path = "players." + uuid;
        if (!yaml.contains(path)) {
            String defaultRank = plugin.getConfig().getString("ranks.default-rank", "Guest");
            PlayerData def = PlayerData.defaultData(uuid, playerName, defaultRank);
            savePlayer(def);
            return def;
        }
        return fromSection(uuid, yaml.getConfigurationSection(path));
    }

    public synchronized void savePlayer(PlayerData data) {
        String path = "players." + data.uuid();
        yaml.set(path + ".name",       data.playerName());
        yaml.set(path + ".rank",       data.rankId());
        yaml.set(path + ".experience", data.experience());
        yaml.set(path + ".money",      data.money());
        yaml.set(path + ".language",   data.language());
        persist();
    }

    public synchronized void saveAll(Collection<PlayerData> players) {
        for (PlayerData data : players) {
            String path = "players." + data.uuid();
            yaml.set(path + ".name",       data.playerName());
            yaml.set(path + ".rank",       data.rankId());
            yaml.set(path + ".experience", data.experience());
            yaml.set(path + ".money",      data.money());
            yaml.set(path + ".language",   data.language());
        }
        persist();
    }

    public List<PlayerData> loadAll() {
        List<PlayerData> result = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection ps = section.getConfigurationSection(key);
                if (ps != null) result.add(fromSection(uuid, ps));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public boolean hasPlayer(UUID uuid) {
        return yaml.contains("players." + uuid);
    }

    private PlayerData fromSection(UUID uuid, ConfigurationSection s) {
        return new PlayerData(
                uuid,
                s.getString("name",       "Unknown"),
                s.getString("rank",       plugin.getConfig().getString("ranks.default-rank", "Guest")),
                s.getLong("experience",   0L),
                s.getDouble("money",      0.0),
                s.getString("language",   "en")
        );
    }

    private void persist() {
        try { yaml.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().warning("[YamlStorage] Failed to save playerdata.yml: " + e.getMessage());
        }
    }

    public File getDataFile() { return dataFile; }
}
