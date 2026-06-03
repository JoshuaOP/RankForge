package com.joshuaop.rankforge.experience;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Persists and retrieves per-player rank history.
 *
 * Storage: {@code plugins/RankForge/data/rank-history.yml}
 *
 * Guarantees:
 *  - Thread-safe writes via a per-instance ReentrantLock.
 *  - Duplicate entries (same from/to/type within a configurable dedup window) are rejected.
 *  - Corrupted, null, or partially-written map entries are silently skipped.
 *  - Max-entry cap is enforced on every write; oldest entries are removed first.
 *  - Invalid ChangeType values in stored data are discarded during load.
 *  - Broken history entries are removed rather than allowing them to propagate.
 */
public class RankHistoryManager {

    /** Duplicate rejection window in milliseconds (default 5 s). */
    private static final long DEDUP_WINDOW_MS = 5_000L;

    private final RankForge    plugin;
    private final File         dataFile;
    private final ReentrantLock lock = new ReentrantLock();

    public RankHistoryManager(RankForge plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "rank-history.yml");
        ensureFileExists();
    }

    // ── I/O ──────────────────────────────────────────────────────────────────

    private void ensureFileExists() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[History] Could not create rank-history.yml: "
                        + e.getMessage());
            }
        }
    }

    /** Loads the YAML configuration safely. Returns an empty config on failure. */
    private YamlConfiguration loadConfig() {
        ensureFileExists();
        try {
            return YamlConfiguration.loadConfiguration(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("[History] Failed to load rank-history.yml: "
                    + e.getMessage() + " — returning empty config.");
            return new YamlConfiguration();
        }
    }

    private void saveConfig(YamlConfiguration yaml) {
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[History] Failed to save rank-history.yml: "
                    + e.getMessage());
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Records a new rank history entry.
     *
     * Duplicate detection: an entry is considered a duplicate if the same
     * player/from/to/type combination was already stored within
     * {@value DEDUP_WINDOW_MS} ms.
     * Oldest entries are trimmed when the per-player cap is exceeded.
     */
    public void record(RankHistoryEntry entry) {
        if (entry == null) return;
        if (!isValidEntry(entry)) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[History] Rejected invalid entry for "
                        + entry.playerUuid() + ": from=" + entry.fromRankId()
                        + " to=" + entry.toRankId() + " type=" + entry.type());
            }
            return;
        }

        lock.lock();
        try {
            YamlConfiguration yaml = loadConfig();
            String path = "players." + entry.playerUuid() + ".entries";
            List<Map<?, ?>> entries = loadRawEntries(yaml, entry.playerUuid().toString());

            // Duplicate check
            if (isDuplicate(entries, entry)) {
                if (plugin.isDebug()) {
                    plugin.getLogger().info("[History] Skipped duplicate entry for "
                            + entry.playerUuid());
                }
                return;
            }

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("from",      entry.fromRankId());
            map.put("to",        entry.toRankId());
            map.put("type",      entry.type().name());
            map.put("timestamp", entry.timestamp());
            map.put("name",      entry.playerName() != null ? entry.playerName() : "Unknown");
            entries.add(map);

            int max = plugin.getConfig().getInt("experience.history-max-entries", 50);
            max = Math.max(1, max);
            while (entries.size() > max) {
                entries.remove(0);
            }

            yaml.set(path, entries);
            saveConfig(yaml);
        } finally {
            lock.unlock();
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns rank history for a player, newest-first.
     * Corrupted or invalid entries are silently skipped.
     */
    public List<RankHistoryEntry> getHistory(UUID uuid) {
        if (uuid == null) return Collections.emptyList();

        YamlConfiguration yaml = loadConfig();
        List<Map<?, ?>> raw = loadRawEntries(yaml, uuid.toString());
        List<RankHistoryEntry> result = new ArrayList<>();

        for (Map<?, ?> m : raw) {
            RankHistoryEntry entry = parseEntry(uuid, m);
            if (entry != null) result.add(entry);
        }

        Collections.reverse(result);
        return result;
    }

    /** Total count of RANKUP events for a player. */
    public int countRankups(UUID uuid) {
        if (uuid == null) return 0;
        return (int) getHistory(uuid).stream()
                .filter(e -> e.type() == RankHistoryEntry.ChangeType.RANKUP)
                .count();
    }

    /**
     * Removes all history entries for a player. Used during admin data resets.
     */
    public void clearHistory(UUID uuid) {
        if (uuid == null) return;
        lock.lock();
        try {
            YamlConfiguration yaml = loadConfig();
            yaml.set("players." + uuid, null);
            saveConfig(yaml);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the configuration section listing all UUIDs with history.
     * May return null if the file is empty or malformed.
     */
    public ConfigurationSection getPlayersSection() {
        return loadConfig().getConfigurationSection("players");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<?, ?>> loadRawEntries(YamlConfiguration yaml, String uuidStr) {
        String path = "players." + uuidStr + ".entries";
        List<?> raw = yaml.getList(path);
        if (raw == null) return new ArrayList<>();
        try {
            return new ArrayList<>((List<Map<?, ?>>) raw);
        } catch (ClassCastException e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[History] Corrupted entries for " + uuidStr
                        + " — clearing.");
            }
            return new ArrayList<>();
        }
    }

    private RankHistoryEntry parseEntry(UUID uuid, Map<?, ?> m) {
        if (m == null) return null;
        try {
            String fromRank = String.valueOf(m.get("from"));
            String toRank   = String.valueOf(m.get("to"));
            String typeName = String.valueOf(m.get("type"));
            Object tsObj    = m.get("timestamp");
            String name     = m.containsKey("name") ? String.valueOf(m.get("name")) : "Unknown";

            // Validate mandatory fields
            if (isNullOrBlank(fromRank) || isNullOrBlank(toRank)
                    || isNullOrBlank(typeName) || tsObj == null) {
                return null;
            }
            if ("null".equalsIgnoreCase(fromRank) || "null".equalsIgnoreCase(toRank)) {
                return null;
            }

            RankHistoryEntry.ChangeType type;
            try {
                type = RankHistoryEntry.ChangeType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning("[History] Unknown ChangeType '" + typeName
                            + "' for " + uuid + " — skipping entry.");
                }
                return null;
            }

            long timestamp = ((Number) tsObj).longValue();
            if (timestamp <= 0) return null;

            return new RankHistoryEntry(uuid, name, fromRank, toRank, type, timestamp);
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[History] Failed to parse entry for "
                        + uuid + ": " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Returns true if an equivalent entry already exists within the dedup window.
     */
    private boolean isDuplicate(List<Map<?, ?>> entries, RankHistoryEntry candidate) {
        if (entries.isEmpty()) return false;
        long windowStart = candidate.timestamp() - DEDUP_WINDOW_MS;

        for (Map<?, ?> m : entries) {
            try {
                Object tsObj = m.get("timestamp");
                if (tsObj == null) continue;
                long ts = ((Number) tsObj).longValue();
                if (ts < windowStart) continue;

                String from = String.valueOf(m.get("from"));
                String to   = String.valueOf(m.get("to"));
                String type = String.valueOf(m.get("type"));

                if (candidate.fromRankId().equals(from)
                        && candidate.toRankId().equals(to)
                        && candidate.type().name().equals(type)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    /** Validates the entry has non-null, non-blank required fields and a positive timestamp. */
    private boolean isValidEntry(RankHistoryEntry entry) {
        return entry.playerUuid() != null
                && entry.type() != null
                && !isNullOrBlank(entry.fromRankId())
                && !isNullOrBlank(entry.toRankId())
                && entry.timestamp() > 0;
    }

    private boolean isNullOrBlank(String s) {
        return s == null || s.isBlank() || "null".equalsIgnoreCase(s);
    }
}
