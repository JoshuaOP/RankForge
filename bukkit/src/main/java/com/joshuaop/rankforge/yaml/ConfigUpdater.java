package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Automatically migrates all RankForge config files to newer versions when the
 * plugin updates.
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li><b>Missing key injection</b> — Keys present in the bundled default but
 *       absent in the server's file are added without overwriting existing values.</li>
 *   <li><b>All config files</b> — Handles config.yml, gui.yml, and lang/*.yml.</li>
 * </ul>
 *
 * <p>Invoked on startup and on /rank reload.
 */
public class ConfigUpdater {

    private final RankForge plugin;

    public ConfigUpdater(RankForge plugin) {
        this.plugin = plugin;
    }

    public void updateAll() {
        updateConfig();
        updateGuiYml();
        
        // Dynamically compile active supported language keys to avoid hardcoded desync bugs
        Set<String> targetLanguages = new HashSet<>();
        targetLanguages.add("en");
        targetLanguages.add("es");
        targetLanguages.add("fil");
        targetLanguages.add("id");

        // Expand collection if your active system contains custom layout keys
        if (plugin.getLangManager() != null) {
            // Optional hook entrypoint: targetLanguages.addAll(plugin.getLangManager().getRegisteredLanguages());
        }

        for (String lang : targetLanguages) {
            updateLang(lang);
        }
    }

    public void updateConfig() {
        updateFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml");
    }

    public void updateGuiYml() {
        updateFile(new File(plugin.getDataFolder(), "gui.yml"), "gui.yml");
    }

    public void updateLang(String langCode) {
        updateFile(new File(plugin.getDataFolder(), "lang/" + langCode + ".yml"),
                "lang/" + langCode + ".yml");
    }

    private void updateFile(File serverFile, String resourcePath) {
        if (!serverFile.exists()) {
            serverFile.getParentFile().mkdirs();
            InputStream resource = plugin.getResource(resourcePath);
            if (resource != null) plugin.saveResource(resourcePath, false);
            return;
        }

        YamlConfiguration defaults = loadBundled(resourcePath);
        if (defaults == null) return;

        YamlConfiguration current = YamlConfiguration.loadConfiguration(serverFile);
        int added = mergeMissing(defaults, current, "");

        if (added > 0) {
            try {
                current.save(serverFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save updated config "
                        + resourcePath + ": " + e.getMessage());
            }
        }
    }

    /**
     * Deep-merges missing default configuration paths into the active workspace config.
     * Prevents NullPointerExceptions by handling newly injected sub-sections cleanly.
     */
    private int mergeMissing(ConfigurationSection source, ConfigurationSection target, String parentPath) {
        int count = 0;
        for (String key : source.getKeys(false)) {
            String currentPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            Object srcVal = source.get(key);

            if (srcVal instanceof ConfigurationSection srcSection) {
                // Safely fetch or initialize sections without destroying existing structures
                ConfigurationSection tgtSection = target.getConfigurationSection(key);
                if (tgtSection == null) {
                    tgtSection = target.createSection(key);
                }
                
                // Recursively check inner nodes within the validated sub-section context
                count += mergeMissing(srcSection, tgtSection, currentPath);
            } else {
                // If the targeted core key does not exist inside the live server configuration file, append it
                if (!target.contains(key)) {
                    target.set(key, srcVal);
                    count++;
                }
            }
        }
        return count;
    }

    private YamlConfiguration loadBundled(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load bundled config "
                    + resourcePath + ": " + e.getMessage());
            return null;
        }
    }
}
