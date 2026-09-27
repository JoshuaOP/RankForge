package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the ranks.yml file: loading, saving, and hot-reload.
 * Holds the parsed YamlConfiguration instance so root properties like 'default-rank' can be read.
 *
 * Auto-save behaviour:
 * - Every call to saveAsync() is debounced — only the most recent call within
 * the DEBOUNCE_TICKS window actually writes to disk. This prevents save spam
 * when many fields are changed in quick succession.
 * - saveSync() is reserved for plugin shutdown; it cancels any pending debounce
 * and writes immediately on the calling thread.
 */
public class RankYamlManager {

    /** Delay before the actual write fires (ticks). 20 ticks = 1 second. */
    private static final long DEBOUNCE_TICKS = 20L;

    private final RankForge      plugin;
    private final Logger         log;
    private final File           ranksFile;
    private final YamlLoader     loader;
    private final YamlSerializer serializer;
    private final Object         lock = new Object();

    /** Monotone counter — only the task whose id matches the current value writes. */
    private final AtomicInteger saveGeneration = new AtomicInteger(0);

    private LinkedHashMap<String, RankModel> ranks  = new LinkedHashMap<>();
    private YamlConfiguration                config = new YamlConfiguration();

    public RankYamlManager(RankForge plugin) {
        this.plugin     = plugin;
        this.log        = plugin.getLogger();
        this.ranksFile  = new File(plugin.getDataFolder(), "ranks.yml");
        this.loader     = new YamlLoader(log);
        this.serializer = new YamlSerializer();
    }

    public void initialize() {
        if (!ranksFile.exists()) {
            plugin.saveResource("ranks.yml", false);
        }
        load();
    }

    public void load() {
        YamlConfiguration loadedConfig = loadUtf8Yaml(ranksFile);
        LinkedHashMap<String, RankModel> loadedRanks = loader.loadFrom(ranksFile);

        synchronized (lock) {
            this.config = loadedConfig;
            this.ranks = loadedRanks;
        }
    }

    public void hotReload() {
        load();
        if (plugin.isDebug()) {
            synchronized (lock) {
                log.info("[RankYaml] Hot-reload complete — " + ranks.size() + " ranks loaded.");
            }
        }
    }

    /**
     * Schedule an asynchronous save of all current ranks to ranks.yml.
     * Calls within DEBOUNCE_TICKS of each other are collapsed — only the last
     * one actually writes, preventing rapid-fire disk writes from the GUI.
     *
     * @param onComplete called on the main thread after the write (may be null)
     */
    public void saveAsync(Runnable onComplete) {
        int gen = saveGeneration.incrementAndGet();
        
        List<RankModel> snapshot;
        String currentDefaultRank;
        
        synchronized (lock) {
            snapshot = new ArrayList<>(ranks.values());
            currentDefaultRank = config.getString("default-rank", "Guest");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (saveGeneration.get() != gen) return;

                YamlConfiguration cfg = serializer.serialize(snapshot);
                cfg.set("default-rank", currentDefaultRank);
                
                try {
                    cfg.save(ranksFile);
                    synchronized (lock) {
                        config = cfg;
                    }
                    if (plugin.isDebug())
                        log.info("[RankYaml] Auto-saved " + snapshot.size() + " ranks.");
                } catch (IOException e) {
                    log.severe("[RankYaml] Failed to save ranks.yml: " + e.getMessage());
                }
                if (onComplete != null) {
                    plugin.getServer().getScheduler().runTask(plugin, onComplete);
                }
            }
        }.runTaskLaterAsynchronously(plugin, DEBOUNCE_TICKS);
    }

    /**
     * Save all ranks synchronously. Use on plugin shutdown only.
     * Bumps the generation counter so any pending debounce task becomes a no-op.
     */
    public void saveSync() {
        saveGeneration.incrementAndGet();
        
        List<RankModel> snapshot;
        String currentDefaultRank;

        synchronized (lock) {
            snapshot = new ArrayList<>(ranks.values());
            currentDefaultRank = config.getString("default-rank", "Guest");
        }

        YamlConfiguration cfg = serializer.serialize(snapshot);
        cfg.set("default-rank", currentDefaultRank);
        
        try {
            cfg.save(ranksFile);
            synchronized (lock) {
                config = cfg;
            }
        } catch (IOException e) {
            log.severe("[RankYaml] Failed to save ranks.yml on shutdown: " + e.getMessage());
        }
    }

    /**
     * Update a single rank in memory.
     *
     * @param model   the updated rank model
     * @param persist true → trigger a debounced async save immediately
     */
    public void updateRank(RankModel model, boolean persist) {
        synchronized (lock) {
            ranks.put(model.getId(), model);
        }
        if (persist) saveAsync(null);
    }

    /**
     * Remove a rank from memory and auto-save.
     */
    public void deleteRank(String rankId) {
        synchronized (lock) {
            ranks.remove(rankId);
        }
        saveAsync(null);
    }

    /**
     * Returns a thread-safe defensive copy of the ranks map to prevent 
     * external callers from mutating internal state without synchronization.
     */
    public LinkedHashMap<String, RankModel> getRanks() {
        synchronized (lock) {
            return new LinkedHashMap<>(ranks);
        }
    }

    public RankModel getRank(String id) {
        synchronized (lock) {
            return ranks.get(id);
        }
    }

    public boolean rankExists(String id) {
        synchronized (lock) {
            return ranks.containsKey(id);
        }
    }

    public File getRanksFile() {
        return ranksFile;
    }
    
    /** Exposes the active YamlConfiguration instance context for direct queries. */
    public YamlConfiguration getConfig() {
        synchronized (lock) {
            return config;
        }
    }

    private YamlConfiguration loadUtf8Yaml(File file) {
        YamlConfiguration yamlConfig = new YamlConfiguration();
        if (!file.exists()) return yamlConfig;
        
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            yamlConfig.load(reader);
        } catch (Exception e) {
            log.log(Level.SEVERE, "[RankYaml] Failed to load configuration file using UTF-8: " + file.getName(), e);
        }
        return yamlConfig;
    }
}
