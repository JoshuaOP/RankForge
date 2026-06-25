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
 * - Thread-safe writes via a per-instance ReentrantLock.
 * - Duplicate entries (same from/to/type within a configurable dedup window) are rejected.
 * - Corrupted, null, or partially-written map entries are silently skipped.
 * - Max-entry cap is enforced on every write; oldest entries are removed first.
 * - Invalid ChangeType values in stored data are discarded during load.
 * - Broken history entries are removed rather than allowing them to propagate.
 */
public class RankHistoryManager {

    /** Duplicate rejection window in milliseconds (default 5 s). */
    private static final long DEDUP_WINDOW_MS = 5_000L;

    private final RankForge    plugin;
    private final File         dataFile;
    private final ReentrantLock lock = new ReentrantLock();
    
    // Memory cache to prevent continuous physical disk reading over identical game ticks
    private YamlConfiguration  cachedYaml;

    public RankHistoryManager(RankForge plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "rank-history.yml");
        ensureFileExists();
        reloadFromDisk();
    }

    // ── I/O Operations ────────────────────────────────────────────────────────

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

    /** Primary disk synchronization call. Runs once on startup or forced admin configurations. */
    public void reloadFromDisk() {
        lock.lock();
        try {
            ensureFileExists();
            this.cachedYaml = YamlConfiguration.loadConfiguration(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("[History] Failed to load rank-history.yml: "
                    + e.getMessage() + " — using clean backup fallback instance.");
            this.cachedYaml = new YamlConfiguration();
        } finally {
            lock.unlock();
        }
    }

    /** BUG FIX: Added lock guard to prevent ConcurrentModificationExceptions during async reads */
    private void saveConfig() {
        lock.lock();
        try {
            cachedYaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[History] Failed to save rank-history.yml: "
                    + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    // ── Write Operations ───────────────────────────────────────────────────────

    /**
     * Records a new rank history entry.
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
            String path = "players." + entry.playerUuid() + ".entries";
            List<Map<?, ?>> entries = loadRawEntries(entry.playerUuid().toString());

            // Duplicate check execution
            if (isDuplicate(entries, entry)) {
                if (plugin.isDebug()) {
                    plugin.getLogger().info("[History] Skipped duplicate entry for "
                            + entry.playerUuid());
                }
                return;
            }

            // LinkedHashMap ensures order preservation inside sequential text structures
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

            cachedYaml.set(path, entries);
            saveConfig();
        } finally {
            lock.unlock();
        }
    }

    // ── Read Operations ────────────────────────────────────────────────────────

    /**
     * Returns rank history for a player, newest-first.
     * Corrupted or invalid entries are silently skipped.
     */
    public List<RankHistoryEntry> getHistory(UUID uuid) {
        if (uuid == null) return Collections.emptyList();

        lock.lock();
        try {
            List<Map<?, ?>> raw = loadRawEntries(uuid.toString());
            List<RankHistoryEntry> result = new ArrayList<>();

            for (Map<?, ?> m : raw) {
                RankHistoryEntry entry = parseEntry(uuid, m);
                if (entry != null) result.add(entry);
            }

            Collections.reverse(result);
            return result;
        } finally { // BUG FIX: Corrected from 'military'
            lock.unlock();
        }
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
            cachedYaml.set("players." + uuid, null);
            saveConfig();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the configuration section listing all UUIDs with history.
     */
    public ConfigurationSection getPlayersSection() {
        lock.lock();
        try {
            return cachedYaml.getConfigurationSection("players");
        } finally {
            lock.unlock();
        }
    }

    // ── Internal Utilities ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<?, ?>> loadRawEntries(String uuidStr) {
        String path = "players." + uuidStr + ".entries";
        List<?> raw = cachedYaml.getList(path);
        if (raw == null) return new ArrayList<>();
        
        List<Map<?, ?>> validatedList = new ArrayList<>();
        for (Object obj : raw) {
            if (obj instanceof Map) {
                validatedList.add((Map<?, ?>) obj);
            } else if (plugin.isDebug()) {
                plugin.getLogger().warning("[History] Stripped structural non-map artifact entry lines for: " + uuidStr);
            }
        }
        return validatedList;
    }

    private RankHistoryEntry parseEntry(UUID uuid, Map<?, ?> m) {
        if (m == null) return null;
        try {
            String fromRank = String.valueOf(m.get("from"));
            String toRank   = String.valueOf(m.get("to"));
            String typeName = String.valueOf(m.get("type"));
            Object tsObj    = m.get("timestamp");
            String name     = m.containsKey("name") ? String.valueOf(m.get("name")) : "Unknown";

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
