package com.joshuaop.rankforge.quest;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Loads quest definitions and tracks per-player quest progress.
 *
 * <p>Quests are multi-step challenge sequences defined in
 * {@code plugins/RankForge/quests.yml}. Progress (current step) is stored in
 * {@code plugins/RankForge/data/quest-progress.yml}.
 *
 * <p>When a challenge is completed, {@link #onChallengeCompleted(Player, String)}
 * is called by {@link com.joshuaop.rankforge.challenge.RankChallengeManager} to
 * advance matching quest steps.
 */
public class RankQuestManager {

    private final RankForge                  plugin;
    private final Map<String, RankQuest>     quests = new LinkedHashMap<>();
    private final File                       progressFile;
    private       YamlConfiguration          progressYaml;
    private       File                       questsFile;

    public RankQuestManager(RankForge plugin) {
        this.plugin       = plugin;
        File dataDir      = new File(plugin.getDataFolder(), "data");
        dataDir.mkdirs();
        this.progressFile = new File(dataDir, "quest-progress.yml");
        this.questsFile   = new File(plugin.getDataFolder(), "quests.yml");

        loadProgressData();
        loadQuests();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /** Parse all quests from quests.yml. */
    public void loadQuests() {
        quests.clear();

        if (!questsFile.exists()) {
            plugin.saveResource("quests.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(questsFile);
        ConfigurationSection root = cfg.getConfigurationSection("quests");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection qs = root.getConfigurationSection(id);
            if (qs == null) continue;

            List<RankQuest.Step> steps = new ArrayList<>();
            List<?> rawSteps = qs.getList("steps", List.of());
            for (Object rawStep : rawSteps) {
                if (rawStep instanceof Map<?, ?> map) {
                    Object cid = map.get("challenge");
                    if (cid != null) steps.add(new RankQuest.Step(cid.toString()));
                }
            }

            quests.put(id, new RankQuest(
                    id,
                    qs.getString("name",           id),
                    qs.getString("description",    ""),
                    qs.getString("rank-required",  ""),
                    qs.getBoolean("repeatable",    false),
                    steps,
                    qs.getLong("reward-xp",        0L),
                    qs.getDouble("reward-money",   0.0),
                    qs.getStringList("reward-commands"),
                    qs.getString("reward-message", "§aQuest complete!")
            ));
        }

        plugin.getLogger().info("[Quests] Loaded " + quests.size() + " quest(s).");
    }

    private void loadProgressData() {
        if (!progressFile.exists()) {
            try { progressFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().warning("[Quests] Could not create quest-progress.yml");
            }
        }
        progressYaml = YamlConfiguration.loadConfiguration(progressFile);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called when a player completes a challenge.
     * Advances any quest steps that require this challenge.
     */
    public void onChallengeCompleted(Player player, String challengeId) {
        for (RankQuest quest : quests.values()) {
            if (isCompleted(player, quest.getId()) && !quest.isRepeatable()) continue;

            int currentStep = getCurrentStep(player, quest.getId());
            if (currentStep >= quest.getTotalSteps()) continue;

            RankQuest.Step step = quest.getSteps().get(currentStep);
            if (step.challengeId().equalsIgnoreCase(challengeId)) {
                advanceQuest(player, quest);
            }
        }
    }

    /** Returns the active step index (0-based) for this quest. */
    public int getCurrentStep(Player player, String questId) {
        return progressYaml.getInt(key(player, questId, "currentStep"), 0);
    }

    /** Returns true if the player has completed this quest. */
    public boolean isCompleted(Player player, String questId) {
        return progressYaml.getBoolean(key(player, questId, "completed"), false);
    }

    /** Returns all quests available to this player (rank filter applied). */
    public List<RankQuest> getAvailable(Player player) {
        String rankId = getRankId(player);
        List<RankQuest> result = new ArrayList<>();
        for (RankQuest q : quests.values()) {
            if (q.hasRankRequirement() && !q.getRankRequired().equalsIgnoreCase(rankId)) continue;
            if (isCompleted(player, q.getId()) && !q.isRepeatable()) continue;
            result.add(q);
        }
        return result;
    }

    public Map<String, RankQuest> getAllQuests() { return Collections.unmodifiableMap(quests); }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void advanceQuest(Player player, RankQuest quest) {
        int nextStep = getCurrentStep(player, quest.getId()) + 1;

        if (nextStep >= quest.getTotalSteps()) {
            // Quest complete!
            completeQuest(player, quest);
        } else {
            // Advance to next step
            synchronized (this) {
                progressYaml.set(key(player, quest.getId(), "currentStep"), nextStep);
                saveProgress();
            }
            RankQuest.Step nextStepObj = quest.getSteps().get(nextStep);
            player.sendMessage("§6[RankForge] §eQuest §6" + quest.getName()
                    + "§e — step " + (nextStep + 1) + "/" + quest.getTotalSteps()
                    + ": §7Complete challenge §e" + nextStepObj.challengeId());
        }
    }

    private void completeQuest(Player player, RankQuest quest) {
        // Grant rewards
        if (quest.getRewardXp() > 0 && plugin.getExperienceManager() != null) {
            plugin.getExperienceManager().award(player, quest.getRewardXp());
        }
        if (quest.getRewardMoney() > 0 && plugin.getSoftDependency().hasVault()) {
            plugin.getSoftDependency().getVaultEconomy()
                  .depositPlayer(player, quest.getRewardMoney());
        }
        for (String cmd : quest.getRewardCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()));
        }
        if (!quest.getRewardMessage().isBlank()) {
            player.sendMessage(quest.getRewardMessage().replace("&", "§"));
        }

        // Persist completion
        synchronized (this) {
            if (quest.isRepeatable()) {
                progressYaml.set(key(player, quest.getId(), "currentStep"), 0);
            } else {
                progressYaml.set(key(player, quest.getId(), "completed"), true);
            }
            saveProgress();
        }
    }

    private synchronized void saveProgress() {
        try { progressYaml.save(progressFile); }
        catch (IOException e) {
            plugin.getLogger().warning("[Quests] Failed to save quest-progress.yml");
        }
    }

    private String key(Player player, String questId, String field) {
        return "players." + player.getUniqueId() + "." + questId + "." + field;
    }

    private String getRankId(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        var data  = cache.get(player.getUniqueId());
        return data != null ? data.rankId() : plugin.getRankManager().getDefaultRankId();
    }
}
