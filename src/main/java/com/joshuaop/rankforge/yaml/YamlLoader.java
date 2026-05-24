package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/**
 * Reads ranks.yml and builds immutable RankModel objects.
 * All YAML parsing is isolated here.
 */
public class YamlLoader {

    private final Logger log;

    public YamlLoader(Logger log) {
        this.log = log;
    }

    /**
     * Load all ranks from the given file into an ordered map.
     * Returns an empty map if the file is missing or has no 'ranks' section.
     */
    public LinkedHashMap<String, RankModel> loadFrom(File file) {
        LinkedHashMap<String, RankModel> result = new LinkedHashMap<>();

        if (!file.exists()) {
            log.warning("[YamlLoader] ranks.yml not found: " + file.getPath());
            return result;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ranksSection = cfg.getConfigurationSection("ranks");

        if (ranksSection == null) {
            log.warning("[YamlLoader] No 'ranks' section found in ranks.yml!");
            return result;
        }

        for (String key : ranksSection.getKeys(false)) {
            ConfigurationSection sec = ranksSection.getConfigurationSection(key);
            if (sec == null) continue;
            try {
                result.put(key, parseRank(key, sec));
            } catch (Exception e) {
                log.warning("[YamlLoader] Skipped rank '" + key + "': " + e.getMessage());
            }
        }

        log.info("[YamlLoader] Loaded " + result.size() + " ranks from ranks.yml.");
        return result;
    }

    private RankModel parseRank(String id, ConfigurationSection sec) {
        ConfigurationSection req = sec.getConfigurationSection("requirements");

        return new RankModel.Builder(id)
                .displayName(sec.getString("display-name", "§7" + id))
                .nextRankId(sec.getString("next-rank", ""))
                .slot(sec.getInt("slot", 11))
                .material(sec.getString("material", "GRAY_WOOL"))
                .lore(sec.getStringList("lore"))
                .requiredMoney(req  != null ? req.getDouble("money",      0)   : 0)
                .requiredXpLevel(req != null ? req.getInt("xp-level",     0)   : 0)
                .requiredPermission(req != null ? req.getString("permission", "") : "")
                .permissions(sec.getStringList("permissions"))
                .chatPrefix(sec.getString("chat-prefix", ""))
                .commands(sec.getStringList("commands"))
                .build();
    }
}
