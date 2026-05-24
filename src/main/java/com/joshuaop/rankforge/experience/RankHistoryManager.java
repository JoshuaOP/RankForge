package com.joshuaop.rankforge.experience;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Persists and retrieves per-player rank history.
 *
 * <p>Data is stored in {@code plugins/RankForge/data/rank-history.yml}.
 * Each player has a list of history entries ordered oldest → newest.
 * A configurable maximum entry count per player prevents unbounded growth
 * (config key: {@code experience.history-max-entries}, default 50).
 */
public class RankHistoryManager {

    private final RankForge            plugin;
    private final File                 dataFile;
    private       YamlConfiguration    yaml;

    public RankHistoryManager(RankForge plugin) {
        this.plugin   = plugin;
        File dataDir  = new File(plugin.getDataFolder(), "data");
        dataDir.mkdirs();
        this.dataFile = new File(dataDir, "rank-history.yml");
        load();
    }

    // ── I/O ──────────────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().warning("[History] Could not create rank-history.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(dataFile);
    }

    private synchronized void save() {
        try { yaml.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().warning("[History] Failed to save rank-history.yml: " + e.getMessage());
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Record a new rank history entry for a player.
     * Trims oldest entries if the maximum is exceeded.
     */
    public synchronized void record(RankHistoryEntry entry) {
        String path  = "players." + entry.playerUuid() + ".entries";
        List<Map<?, ?>> entries = getOrCreate(entry.playerUuid().toString());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("from",      entry.fromRankId());
        map.put("to",        entry.toRankId());
        map.put("type",      entry.type().name());
        map.put("timestamp", entry.timestamp());
        map.put("name",      entry.playerName());
        entries.add(map);

        int max = plugin.getConfig().getInt("experience.history-max-entries", 50);
        while (entries.size() > max) entries.remove(0);

        yaml.set(path, entries);
        save();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the rank history for a player, newest first.
     */
    public List<RankHistoryEntry> getHistory(UUID uuid) {
        List<Map<?, ?>> raw = getOrCreate(uuid.toString());
        List<RankHistoryEntry> result = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            try {
                result.add(new RankHistoryEntry(
                        uuid,
                        String.valueOf(m.get("name")),
                        String.valueOf(m.get("from")),
                        String.valueOf(m.get("to")),
                        RankHistoryEntry.ChangeType.valueOf(String.valueOf(m.get("type"))),
                        ((Number) m.get("timestamp")).longValue()
                ));
            } catch (Exception ignored) {}
        }
        Collections.reverse(result);   // newest first
        return result;
    }

    /** Total number of rank-ups for a given player. */
    public int countRankups(UUID uuid) {
        return (int) getHistory(uuid).stream()
                .filter(e -> e.type() == RankHistoryEntry.ChangeType.RANKUP)
                .count();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<?, ?>> getOrCreate(String uuidStr) {
        String path = "players." + uuidStr + ".entries";
        List<?> raw = yaml.getList(path);
        if (raw == null) return new ArrayList<>();
        try { return new ArrayList<>((List<Map<?, ?>>) raw); }
        catch (ClassCastException e) { return new ArrayList<>(); }
    }

    /** Returns a section listing all UUIDs with history. */
    public ConfigurationSection getPlayersSection() {
        return yaml.getConfigurationSection("players");
    }
}
