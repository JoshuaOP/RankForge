package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collection;
import java.util.Map;

/**
 * Converts RankModel objects into YAML-compatible structures.
 * Only writes non-default values for optional requirement fields to keep ranks.yml clean.
 */
public class YamlSerializer {

    public YamlConfiguration serialize(Collection<RankModel> ranks) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (RankModel r : ranks) write(cfg, r);
        return cfg;
    }

    public void serializeOne(YamlConfiguration cfg, RankModel r) {
        write(cfg, r);
    }

    private void write(YamlConfiguration cfg, RankModel r) {
        String b = "ranks." + r.getId();

        // ── Identity ──────────────────────────────────────────────────────────
        cfg.set(b + ".display-name",  r.getDisplayName());
        cfg.set(b + ".next-rank",     r.getNextRankId());
        cfg.set(b + ".slot",          r.getSlot());
        cfg.set(b + ".material",      r.getMaterial());
        cfg.set(b + ".chat-prefix",   r.getChatPrefix());
        cfg.set(b + ".lore",          r.getLore());
        cfg.set(b + ".permissions",   r.getPermissions());
        cfg.set(b + ".commands",      r.getCommands());

        // ── Core requirements (always written) ────────────────────────────────
        cfg.set(b + ".requirements.money",      r.getRequiredMoney());
        cfg.set(b + ".requirements.xp-level",   r.getRequiredXpLevel());
        cfg.set(b + ".requirements.permission", r.getRequiredPermission());

        // ── Optional requirements (only written when non-default) ─────────────
        if (r.getRequiredPlaytimeMinutes() > 0)
            cfg.set(b + ".requirements.playtime-minutes",  r.getRequiredPlaytimeMinutes());

        if (r.getRequiredMobKills() > 0)
            cfg.set(b + ".requirements.mob-kills",         r.getRequiredMobKills());

        if (r.getRequiredBlockBreaks() > 0)
            cfg.set(b + ".requirements.block-breaks",      r.getRequiredBlockBreaks());

        if (r.getRequiredStatisticId() != null && !r.getRequiredStatisticId().isBlank()) {
            cfg.set(b + ".requirements.statistic-id",      r.getRequiredStatisticId());
            cfg.set(b + ".requirements.statistic-value",   r.getRequiredStatisticValue());
        }

        if (!r.getRequiredQuests().isEmpty())
            cfg.set(b + ".requirements.quests",            r.getRequiredQuests());

        if (!r.getRequiredWorlds().isEmpty())
            cfg.set(b + ".requirements.worlds",            r.getRequiredWorlds());

        if (!r.getRequiredItems().isEmpty()) {
            for (Map.Entry<String, Integer> entry : r.getRequiredItems().entrySet()) {
                cfg.set(b + ".requirements.items." + entry.getKey(), entry.getValue());
            }
        }
    }
}
