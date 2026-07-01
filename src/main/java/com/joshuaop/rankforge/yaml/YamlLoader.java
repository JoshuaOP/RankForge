package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.joshuaop.rankforge.util.FormatUtil;

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
                .requiredPlaytimeMinutes(parsePlaytimeToMinutes(id, req))
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

    /**
     * Resolves the playtime requirement for a rank.
     *
     * <p>Priority:
     * <ol>
     *   <li>New unified key: {@code playtime: "5d 5hr 5m 5s"}</li>
     *   <li>Legacy key (backward-compatible): {@code playtime-minutes: 60}</li>
     * </ol>
     *
     * <p>Supported units (case-insensitive): {@code d} days, {@code hr} hours,
     * {@code m} minutes, {@code s} seconds (truncated to whole minutes).
     * Negative values are rejected and default to {@code 0}.
     */
    private long parsePlaytimeToMinutes(String rankId, ConfigurationSection req) {
        if (req == null) return 0;

        // ── New unified format: playtime: "5d 5hr 5m 5s" ─────────────────────
        if (req.isSet("playtime")) {
            String raw = req.getString("playtime", "").trim();
            if (!raw.isEmpty()) {
                try {
                    long minutes = FormatUtil.parsePlaytimeString(raw);
                    return minutes;
                } catch (IllegalArgumentException e) {
                    log.warning("[YamlLoader] Rank '" + rankId
                            + "': invalid playtime value '" + raw
                            + "' — " + e.getMessage() + ". Defaulting to 0.");
                    return 0;
                }
            }
        }

        // ── Legacy backward-compatible key: playtime-minutes ──────────────────
        long minutes = req.getLong("playtime-minutes", 0);
        if (minutes < 0) {
            log.warning("[YamlLoader] Rank '" + rankId
                    + "': negative playtime-minutes value '" + minutes
                    + "' — defaulting to 0.");
            return 0;
        }
        return minutes;
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
