package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * YAML-based player data storage.
 * Default fallback when MySQL is unavailable.
 * Path: plugins/RankForge/data/playerdata.yml
 *
 * Schema versions:
 *   v1 — legacy (legacy-rank-node field)
 *   v2 — experience, money, language fields
 *   v3 — adds block-breaks (BlockBreakTracker exact counter)
 *   v4 — adds playtime-minutes (real wall-clock playtime, not tick-based)
 *   v5 — adds completed-requirements (requirement-type keys manually completed via
 *        /rank bypassreq for the player's current rank; cleared automatically on rank-up)
 */
public class YamlPlayerDataStorage {

    private static final int CURRENT_DATA_VERSION = 5;

    private final RankForge       plugin;
    private final File            dataFile;
    private YamlConfiguration     yaml;

    public YamlPlayerDataStorage(RankForge plugin) {
        this.plugin  = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "playerdata.yml");
        load();
        checkAndMigrateSchema();
    }

    // ── Load / Init ───────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                yaml = YamlConfiguration.loadConfiguration(dataFile);
                yaml.set("data-version", CURRENT_DATA_VERSION);
                persist();
                return;
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create playerdata.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void checkAndMigrateSchema() {
        int savedVersion = yaml.getInt("data-version", 1);
        if (savedVersion >= CURRENT_DATA_VERSION) return;

        plugin.getLogger().info("Migrating player data v" + savedVersion
                + " → v" + CURRENT_DATA_VERSION + "...");

        if (savedVersion < 2) migrateV1ToV2();
        if (savedVersion < 3) migrateV2ToV3();
        if (savedVersion < 4) migrateV3ToV4();
        if (savedVersion < 5) migrateV4ToV5();

        yaml.set("data-version", CURRENT_DATA_VERSION);
        persist();
        plugin.getLogger().info("Player data migration complete.");
    }

    /** v1 → v2: rename legacy-rank-node → rank */
    private void migrateV1ToV2() {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            String path = "players." + uuidStr;
            if (yaml.contains(path + ".legacy-rank-node")) {
                yaml.set(path + ".rank", yaml.getString(path + ".legacy-rank-node"));
                yaml.set(path + ".legacy-rank-node", null);
            }
        }
    }

    /** v2 → v3: add block-breaks field defaulting to 0 for all existing entries */
    private void migrateV2ToV3() {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            String path = "players." + uuidStr + ".block-breaks";
            if (!yaml.contains(path)) {
                yaml.set(path, 0L);
            }
        }
    }

    /**
     * v3 → v4: add playtime-minutes field defaulting to 0 for all existing entries.
     * No conversion from the old vanilla PLAY_ONE_MINUTE statistic is performed because
     * the tick-based stat is inherently inaccurate and would propagate that error forward.
     * Players simply begin accumulating real-world playtime from this point onward.
     */
    private void migrateV3ToV4() {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        int count = 0;
        for (String uuidStr : players.getKeys(false)) {
            String path = "players." + uuidStr + ".playtime-minutes";
            if (!yaml.contains(path)) {
                yaml.set(path, 0L);
                count++;
            }
        }
    }

    /**
     * v4 → v5: add completed-requirements field defaulting to an empty list for all
     * existing entries. No conversion is required since this is a brand-new field.
     */
    private void migrateV4ToV5() {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            String path = "players." + uuidStr + ".completed-requirements";
            if (!yaml.contains(path)) {
                yaml.set(path, new ArrayList<String>());
            }
        }
    }

    // ── Player Read/Write ─────────────────────────────────────────────────────

    public synchronized PlayerData loadPlayer(UUID uuid, String playerName) {
        String path = "players." + uuid;
        if (!yaml.contains(path)) {
            String defaultRank = plugin.getRankManager() != null
                    ? plugin.getRankManager().getDefaultRankId() : "Guest";
            PlayerData def = PlayerData.defaultData(uuid, playerName, defaultRank);
            savePlayer(def);
            return def;
        }

        PlayerData loaded = fromSection(uuid, yaml.getConfigurationSection(path));

        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline() && !loaded.playerName().equals(online.getName()))
            loaded = loaded.withPlayerName(online.getName());

        return loaded;
    }

    public synchronized void savePlayer(PlayerData data) {
        PlayerData stitched = stitchRuntimeData(data);
        write(stitched);
        persist();
    }

    public synchronized void saveAll(Collection<PlayerData> players) {
        for (PlayerData data : players) write(data);
        persist();
    }

    public synchronized List<PlayerData> loadAll() {
        List<PlayerData> result = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection ps = section.getConfigurationSection(key);
                if (ps == null) continue;
                PlayerData loaded = fromSection(uuid, ps);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline())
                    loaded = loaded.withPlayerName(online.getName());
                result.add(loaded);
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public synchronized boolean hasPlayer(UUID uuid) {
        return yaml.contains("players." + uuid);
    }

    public File getDataFile() { return dataFile; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void write(PlayerData data) {
        String path = "players." + data.uuid();
        yaml.set(path + ".name",             data.playerName());
        yaml.set(path + ".rank",             data.rankId());
        yaml.set(path + ".experience",       data.experience());
        yaml.set(path + ".money",            data.money());
        yaml.set(path + ".language",         data.language());
        yaml.set(path + ".block-breaks",     data.blockBreaks());
        yaml.set(path + ".playtime-minutes", data.playTime());
        yaml.set(path + ".completed-requirements", new ArrayList<>(data.completedRequirements()));
    }

    private PlayerData fromSection(UUID uuid, ConfigurationSection s) {
        String defaultRank = plugin.getRankManager() != null
                ? plugin.getRankManager().getDefaultRankId() : "Guest";
        List<String> completedRequirements = s.getStringList("completed-requirements");
        return new PlayerData(
                uuid,
                s.getString("name",             "Unknown"),
                s.getString("rank",             defaultRank),
                s.getLong("experience",          0L),
                s.getDouble("money",             0.0),
                s.getString("language",          "en"),
                s.getLong("block-breaks",        0L),
                s.getLong("playtime-minutes",    0L),
                new java.util.LinkedHashSet<>(completedRequirements)
        );
    }

    private void persist() {
        try { yaml.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().warning("Failed to save playerdata.yml: " + e.getMessage());
        }
    }

    /**
     * Stitches live XP, Vault balance, block-break count, and playtime for online players
     * so saves always reflect the current session state.
     */
    private PlayerData stitchRuntimeData(PlayerData data) {
        Player player = Bukkit.getPlayer(data.uuid());
        if (player == null || !player.isOnline()) return data;

        long liveXp = plugin.getExperienceManager() != null
                ? plugin.getExperienceManager().getXp(player)
                : data.experience();

        double liveMoney = data.money();
        if (plugin.getSoftDependency() != null && plugin.getSoftDependency().hasVault()) {
            try { liveMoney = plugin.getSoftDependency().getBalance(player); }
            catch (Exception ignored) {}
        }

        long liveBlocks = plugin.getBlockBreakTracker() != null
                ? plugin.getBlockBreakTracker().getCount(player.getUniqueId())
                : data.blockBreaks();

        long livePlaytime = plugin.getPlaytimeTracker() != null
                ? plugin.getPlaytimeTracker().getPlayTime(player.getUniqueId())
                : data.playTime();

        return new PlayerData(
                data.uuid(), player.getName(), data.rankId(),
                liveXp, liveMoney, data.language(), liveBlocks, livePlaytime,
                data.completedRequirements()
        );
    }
}
