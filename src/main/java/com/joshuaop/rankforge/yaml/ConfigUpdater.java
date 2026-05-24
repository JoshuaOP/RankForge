package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Automatically migrates all RankForge config files to newer versions when the
 * plugin updates.
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li><b>Missing key injection</b> — Keys present in the bundled default but
 *       absent in the server's file are added without overwriting existing values.</li>
 *   <li><b>Automatic backups</b> — Before any migration, the original file is
 *       copied to {@code <file>.bak.<timestamp>} in the same directory.</li>
 *   <li><b>All config files</b> — Handles {@code config.yml}, {@code gui.yml},
 *       {@code lang/*.yml}, {@code challenges.yml}, and {@code quests.yml}.</li>
 * </ul>
 *
 * <p>Invoked on startup and on {@code /rank reload}.
 */
public class ConfigUpdater {

    private final RankForge plugin;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyyMMdd-HHmmss");

    public ConfigUpdater(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run all updaters (config.yml, gui.yml, all language files,
     * challenges.yml, quests.yml).
     */
    public void updateAll() {
        updateConfig();
        updateGuiYml();
        for (String lang : new String[]{"en", "es", "fil", "id"}) {
            updateLang(lang);
        }
        updateResource("challenges.yml");
        updateResource("quests.yml");
    }

    /**
     * Check and update config.yml if needed.
     */
    public void updateConfig() {
        updateFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml");
    }

    /**
     * Check and update gui.yml if needed.
     */
    public void updateGuiYml() {
        updateFile(new File(plugin.getDataFolder(), "gui.yml"), "gui.yml");
    }

    /**
     * Check and update a language file if needed.
     *
     * @param langCode e.g. "en", "es"
     */
    public void updateLang(String langCode) {
        String resource = "lang/" + langCode + ".yml";
        updateFile(new File(plugin.getDataFolder(), resource), resource);
    }

    /**
     * Check and update any bundled resource file that lives at the plugin root.
     *
     * @param resourceName e.g. "challenges.yml", "quests.yml"
     */
    public void updateResource(String resourceName) {
        updateFile(new File(plugin.getDataFolder(), resourceName), resourceName);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Core file update logic:
     * <ol>
     *   <li>If the file doesn't exist, save the bundled default directly.</li>
     *   <li>Load both the bundled default and the server's version.</li>
     *   <li>If keys are missing, create a backup then merge and save.</li>
     * </ol>
     */
    private void updateFile(File serverFile, String resourcePath) {
        // File doesn't exist → save default and we're done
        if (!serverFile.exists()) {
            serverFile.getParentFile().mkdirs();
            InputStream resource = plugin.getResource(resourcePath);
            if (resource != null) {
                plugin.saveResource(resourcePath, false);
            }
            return;
        }

        // Load bundled default from jar
        YamlConfiguration defaults = loadBundled(resourcePath);
        if (defaults == null) return; // no bundled default for this file

        // Load server's current version
        YamlConfiguration current = YamlConfiguration.loadConfiguration(serverFile);

        // Count missing keys
        int added = mergeMissing(defaults, current, null);

        if (added > 0) {
            // Back up the original before overwriting
            backup(serverFile);

            try {
                current.save(serverFile);
                plugin.getLogger().info("[ConfigUpdater] Updated " + resourcePath
                        + " — added " + added + " missing key(s). Backup created.");
            } catch (IOException e) {
                plugin.getLogger().warning("[ConfigUpdater] Could not save updated "
                        + resourcePath + ": " + e.getMessage());
            }
        } else {
            plugin.getLogger().fine("[ConfigUpdater] " + resourcePath + " is up-to-date.");
        }
    }

    /**
     * Recursively copy keys from {@code source} that are missing in {@code target}.
     *
     * @return number of keys added
     */
    private int mergeMissing(ConfigurationSection source, ConfigurationSection target,
                             String parentPath) {
        int count = 0;
        for (String key : source.getKeys(false)) {
            Object srcVal = source.get(key);

            if (srcVal instanceof ConfigurationSection srcSection) {
                if (!target.contains(key)) target.createSection(key);
                Object tgtObj = target.get(key);
                ConfigurationSection tgtSection = (tgtObj instanceof ConfigurationSection cs)
                        ? cs : target.createSection(key);
                count += mergeMissing(srcSection, tgtSection,
                        parentPath == null ? key : parentPath + "." + key);
            } else {
                if (!target.contains(key)) {
                    target.set(key, srcVal);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Create a timestamped backup of the given file.
     * Backup path: {@code <parent>/<name>.bak.<timestamp>}
     */
    private void backup(File original) {
        String timestamp = DATE_FMT.format(new Date());
        File backup = new File(original.getParentFile(),
                original.getName() + ".bak." + timestamp);
        try {
            Files.copy(original.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("[ConfigUpdater] Could not create backup of "
                    + original.getName() + ": " + e.getMessage());
        }
    }

    /** Load a YAML resource bundled inside the plugin jar. */
    private YamlConfiguration loadBundled(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("[ConfigUpdater] Could not load bundled "
                    + resourcePath + ": " + e.getMessage());
            return null;
        }
    }
}
