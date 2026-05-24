package com.joshuaop.rankforge.challenge;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Loads, tracks, and rewards challenge progress per player.
 *
 * <p>Challenges are defined in {@code plugins/RankForge/challenges.yml}.
 * Progress is persisted in {@code plugins/RankForge/data/challenge-progress.yml}.
 *
 * <p>This class implements {@link Listener} and must be registered with
 * {@code Bukkit.getPluginManager().registerEvents(challengeManager, plugin)}.
 */
public class RankChallengeManager implements Listener {

    private final RankForge                    plugin;
    private final Map<String, RankChallenge>   challenges = new LinkedHashMap<>();
    private final File                         progressFile;
    private       YamlConfiguration            progressYaml;
    private       File                         challengesFile;

    public RankChallengeManager(RankForge plugin) {
        this.plugin        = plugin;
        File dataDir       = new File(plugin.getDataFolder(), "data");
        dataDir.mkdirs();
        this.progressFile  = new File(dataDir, "challenge-progress.yml");
        this.challengesFile = new File(plugin.getDataFolder(), "challenges.yml");

        loadProgressData();
        loadChallenges();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /** Load or create challenges.yml and parse all challenge definitions. */
    public void loadChallenges() {
        challenges.clear();

        if (!challengesFile.exists()) {
            plugin.saveResource("challenges.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(challengesFile);
        ConfigurationSection root = cfg.getConfigurationSection("challenges");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(id);
            if (cs == null) continue;
            try {
                RankChallenge.ChallengeType type =
                        RankChallenge.ChallengeType.valueOf(
                                cs.getString("type", "MANUAL").toUpperCase());
                RankChallenge challenge = new RankChallenge(
                        id,
                        cs.getString("name",         id),
                        cs.getString("description",  ""),
                        type,
                        cs.getInt("target-count",    1),
                        cs.getLong("reward-xp",      0L),
                        cs.getDouble("reward-money", 0.0),
                        cs.getStringList("reward-commands"),
                        cs.getString("rank-required",""),
                        cs.getBoolean("repeatable",  false),
                        cs.getLong("cooldown-hours", 24L)
                );
                challenges.put(id, challenge);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Challenges] Invalid type for challenge '" + id + "': " + e.getMessage());
            }
        }

        plugin.getLogger().info("[Challenges] Loaded " + challenges.size() + " challenge(s).");
    }

    private void loadProgressData() {
        if (!progressFile.exists()) {
            try { progressFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().warning("[Challenges] Could not create challenge-progress.yml");
            }
        }
        progressYaml = YamlConfiguration.loadConfiguration(progressFile);
    }

    // ── Event Tracking ────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        progressType(player, RankChallenge.ChallengeType.MINE_BLOCK, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        progressType(killer, RankChallenge.ChallengeType.KILL_ENTITY, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        progressType(player, RankChallenge.ChallengeType.CRAFT_ITEM, 1);
    }

    /** Called by RankService on player rank-up for RANKUP-type challenges. */
    public void onRankup(Player player) {
        progressType(player, RankChallenge.ChallengeType.RANKUP, 1);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Manually add progress to a specific challenge for a player.
     * Used by the Custom Requirement API and external plugins.
     */
    public void addProgress(Player player, String challengeId, int amount) {
        RankChallenge challenge = challenges.get(challengeId);
        if (challenge == null) return;
        if (challenge.hasRankRequirement() && !getRankId(player).equals(challenge.getRankRequired()))
            return;

        int current = getProgress(player, challengeId);
        if (isCompleted(player, challengeId) && !challenge.isRepeatable()) return;
        if (isOnCooldown(player, challengeId)) return;

        int newProgress = current + amount;
        setProgress(player, challengeId, newProgress);

        if (newProgress >= challenge.getTargetCount()) {
            completeChallenge(player, challenge);
        }
    }

    /** Returns the current progress count for a player on a challenge. */
    public int getProgress(Player player, String challengeId) {
        return progressYaml.getInt(key(player, challengeId, "progress"), 0);
    }

    /** Returns true if the player has completed this challenge (and it's not repeatable). */
    public boolean isCompleted(Player player, String challengeId) {
        return progressYaml.getBoolean(key(player, challengeId, "completed"), false);
    }

    /** Returns true if the challenge is on cooldown for this player. */
    public boolean isOnCooldown(Player player, String challengeId) {
        RankChallenge challenge = challenges.get(challengeId);
        if (challenge == null || !challenge.isRepeatable()) return false;
        long lastComplete = progressYaml.getLong(key(player, challengeId, "lastComplete"), 0L);
        long cooldownMs   = challenge.getCooldownHours() * 3_600_000L;
        return System.currentTimeMillis() - lastComplete < cooldownMs;
    }

    /** List of all loaded challenges available to a player. */
    public List<RankChallenge> getAvailable(Player player) {
        String rankId = getRankId(player);
        List<RankChallenge> result = new ArrayList<>();
        for (RankChallenge c : challenges.values()) {
            if (!c.hasRankRequirement() || c.getRankRequired().equalsIgnoreCase(rankId)) {
                if (!isCompleted(player, c.getId()) || c.isRepeatable()) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    public Map<String, RankChallenge> getAllChallenges() { return Collections.unmodifiableMap(challenges); }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void progressType(Player player, RankChallenge.ChallengeType type, int amount) {
        for (RankChallenge c : challenges.values()) {
            if (c.getType() == type) addProgress(player, c.getId(), amount);
        }
    }

    private void completeChallenge(Player player, RankChallenge challenge) {
        // Award rewards
        if (challenge.getRewardXp() > 0 && plugin.getExperienceManager() != null) {
            plugin.getExperienceManager().award(player, challenge.getRewardXp());
        }
        if (challenge.getRewardMoney() > 0 && plugin.getSoftDependency().hasVault()) {
            plugin.getSoftDependency().getVaultEconomy()
                  .depositPlayer(player, challenge.getRewardMoney());
        }
        for (String cmd : challenge.getRewardCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }

        player.sendMessage("§6[RankForge] §aChallenge complete: §e" + challenge.getName() + "§a!");

        // Mark completed
        synchronized (this) {
            if (challenge.isRepeatable()) {
                setProgress(player, challenge.getId(), 0);
                progressYaml.set(key(player, challenge.getId(), "lastComplete"), System.currentTimeMillis());
            } else {
                progressYaml.set(key(player, challenge.getId(), "completed"), true);
            }
            saveProgress();
        }
    }

    private synchronized void setProgress(Player player, String challengeId, int value) {
        progressYaml.set(key(player, challengeId, "progress"), value);
        saveProgress();
    }

    private void saveProgress() {
        try { progressYaml.save(progressFile); }
        catch (IOException e) {
            plugin.getLogger().warning("[Challenges] Failed to save challenge-progress.yml");
        }
    }

    private String key(Player player, String challengeId, String field) {
        return "players." + player.getUniqueId() + "." + challengeId + "." + field;
    }

    private String getRankId(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        var data  = cache.get(player.getUniqueId());
        return data != null ? data.rankId() : plugin.getRankManager().getDefaultRankId();
    }
}
