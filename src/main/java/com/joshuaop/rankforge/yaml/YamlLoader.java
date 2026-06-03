package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads ranks.yml and builds immutable RankModel objects.
 * All YAML parsing is isolated here.
 * Supports backward-compatible loading of both old and new requirement structures.
 */
public class YamlLoader {

    private final Logger log;

    public YamlLoader(Logger log) {
        this.log = log;
    }

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
                .chatPrefix(sec.getString("chat-prefix", ""))
                .permissions(sec.getStringList("permissions"))
                .commands(sec.getStringList("commands"))
                // ── Core requirements (backward-compatible) ──────────────────
                .requiredMoney(req           != null ? req.getDouble("money",            0)   : 0)
                .requiredXpLevel(req         != null ? req.getInt("xp-level",            0)   : 0)
                .requiredPermission(req      != null ? req.getString("permission",        "") : "")
                .requiredPlaytimeMinutes(req != null ? req.getLong("playtime-minutes",    0)   : 0)
                .requiredMobKills(req        != null ? req.getInt("mob-kills",            0)   : 0)
                // ── New requirements ─────────────────────────────────────────
                .requiredBlockBreaks(req     != null ? req.getInt("block-breaks",         0)   : 0)
                .requiredStatisticId(req     != null ? req.getString("statistic-id",      "") : "")
                .requiredStatisticValue(req  != null ? req.getInt("statistic-value",      0)   : 0)
                .requiredQuests(req          != null ? req.getStringList("quests")              : java.util.Collections.emptyList())
                .requiredWorlds(req          != null ? req.getStringList("worlds")              : java.util.Collections.emptyList())
                .requiredItems(parseItems(req))
                .build();
    }

    private Map<String, Integer> parseItems(ConfigurationSection req) {
        Map<String, Integer> items = new LinkedHashMap<>();
        if (req == null) return items;
        ConfigurationSection itemsSec = req.getConfigurationSection("items");
        if (itemsSec == null) return items;
        for (String key : itemsSec.getKeys(false)) {
            int amount = itemsSec.getInt(key, 1);
            if (amount > 0) items.put(key.toUpperCase(), amount);
        }
        return items;
    }
}
