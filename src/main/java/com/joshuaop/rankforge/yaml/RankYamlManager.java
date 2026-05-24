package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/**
 * Manages the ranks.yml file: loading, saving, hot-reload, and backup.
 * All save operations are async-safe and create a backup before overwriting.
 */
public class RankYamlManager {

    private final RankForge       plugin;
    private final Logger          log;
    private final File            ranksFile;
    private final YamlLoader      loader;
    private final YamlSerializer  serializer;

    private LinkedHashMap<String, RankModel> ranks = new LinkedHashMap<>();

    public RankYamlManager(RankForge plugin) {
        this.plugin     = plugin;
        this.log        = plugin.getLogger();
        this.ranksFile  = new File(plugin.getDataFolder(), "ranks.yml");
        this.loader     = new YamlLoader(log);
        this.serializer = new YamlSerializer();
    }

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Ensure ranks.yml exists (copy bundled default if absent), then load it.
     */
    public void initialize() {
        if (!ranksFile.exists()) {
            plugin.saveResource("ranks.yml", false);
            log.info("[RankYaml] Created default ranks.yml.");
        }
        load();
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    /**
     * Load (or hot-reload) ranks from ranks.yml into memory.
     */
    public void load() {
        ranks = loader.loadFrom(ranksFile);
    }

    /**
     * Hot-reload ranks.yml without restarting the plugin.
     */
    public void hotReload() {
        load();
        log.info("[RankYaml] Hot-reload complete — " + ranks.size() + " ranks loaded.");
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    /**
     * Save all current ranks to ranks.yml asynchronously.
     * Creates a timestamped backup of the current file before overwriting.
     * @param onComplete called on the main thread when done (may be null)
     */
    public void saveAsync(Runnable onComplete) {
        Collection<RankModel> snapshot = ranks.values();

        new BukkitRunnable() {
            @Override
            public void run() {
                backup();
                YamlConfiguration cfg = serializer.serialize(snapshot);
                try {
                    cfg.save(ranksFile);
                    log.info("[RankYaml] Saved " + snapshot.size() + " ranks to ranks.yml.");
                } catch (IOException e) {
                    log.severe("[RankYaml] Failed to save ranks.yml: " + e.getMessage());
                }

                if (onComplete != null) {
                    plugin.getServer().getScheduler().runTask(plugin, onComplete);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Save all ranks synchronously (use on shutdown only).
     */
    public void saveSync() {
        backup();
        YamlConfiguration cfg = serializer.serialize(ranks.values());
        try {
            cfg.save(ranksFile);
        } catch (IOException e) {
            log.severe("[RankYaml] Failed to save ranks.yml: " + e.getMessage());
        }
    }

    /**
     * Update a single rank in memory and optionally persist async.
     */
    public void updateRank(RankModel model, boolean persist) {
        ranks.put(model.getId(), model);
        if (persist) saveAsync(null);
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    private void backup() {
        if (!ranksFile.exists()) return;
        File backupDir = new File(plugin.getDataFolder(), "backups");
        backupDir.mkdirs();
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File   backupFile = new File(backupDir, "ranks-" + timestamp + ".yml");
        try {
            Files.copy(ranksFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.fine("[RankYaml] Backup created: " + backupFile.getName());
        } catch (IOException e) {
            log.warning("[RankYaml] Could not create backup: " + e.getMessage());
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public LinkedHashMap<String, RankModel> getRanks()          { return ranks; }
    public RankModel getRank(String id)                         { return ranks.get(id); }
    public boolean   rankExists(String id)                      { return ranks.containsKey(id); }
    public File      getRanksFile()                             { return ranksFile; }
}
