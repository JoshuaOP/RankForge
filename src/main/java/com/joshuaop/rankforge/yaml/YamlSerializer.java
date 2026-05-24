package com.joshuaop.rankforge.yaml;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collection;

/**
 * Converts RankModel objects into YAML-compatible structures.
 */
public class YamlSerializer {

    /**
     * Serialize all ranks into a fresh YamlConfiguration ready for saving.
     */
    public YamlConfiguration serialize(Collection<RankModel> ranks) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (RankModel r : ranks) write(cfg, r);
        return cfg;
    }

    /**
     * Serialize a single rank into an existing configuration (partial update).
     */
    public void serializeOne(YamlConfiguration cfg, RankModel r) {
        write(cfg, r);
    }

    private void write(YamlConfiguration cfg, RankModel r) {
        String b = "ranks." + r.getId();
        cfg.set(b + ".display-name",            r.getDisplayName());
        cfg.set(b + ".next-rank",               r.getNextRankId());
        cfg.set(b + ".slot",                    r.getSlot());
        cfg.set(b + ".material",                r.getMaterial());
        cfg.set(b + ".chat-prefix",             r.getChatPrefix());
        cfg.set(b + ".lore",                    r.getLore());
        cfg.set(b + ".permissions",             r.getPermissions());
        cfg.set(b + ".commands",                r.getCommands());
        cfg.set(b + ".requirements.money",      r.getRequiredMoney());
        cfg.set(b + ".requirements.xp-level",  r.getRequiredXpLevel());
        cfg.set(b + ".requirements.permission", r.getRequiredPermission());
    }
}
