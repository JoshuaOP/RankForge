package com.joshuaop.rankforge.reward;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.event.DailyRewardClaimEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the daily reward system.
 *
 * <p>Players may claim one reward per configurable cooldown period
 * (default 24 hours). Reward configurations are read from config.yml
 * under {@code daily-rewards}. Last-claim timestamps are persisted in
 * {@code plugins/RankForge/data/daily-rewards.yml}.
 *
 * <h3>config.yml structure:</h3>
 * <pre>
 * daily-rewards:
 *   enabled: true
 *   cooldown-hours: 24
 *   rewards:
 *     global:
 *       xp: 50
 *       money: 100.0
 *       message: "§aYou claimed your daily reward!"
 *       commands: []
 *     Member:
 *       xp: 100
 *       money: 250.0
 *       message: "§aDaily reward claimed as §6Member§a!"
 *       commands:
 *         - "give %player% diamond 1"
 * </pre>
 */
public class DailyRewardManager {

    private final RankForge         plugin;
    private final File              dataFile;
    private       YamlConfiguration yaml;
    private final List<DailyReward> rewards = new ArrayList<>();

    public DailyRewardManager(RankForge plugin) {
        this.plugin  = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data");
        dataDir.mkdirs();
        this.dataFile = new File(dataDir, "daily-rewards.yml");
        load();
        loadRewards();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().warning("[DailyReward] Could not create daily-rewards.yml");
            }
        }
        yaml = YamlConfiguration.loadConfiguration(dataFile);
    }

    /** Parse reward definitions from config.yml. */
    public void loadRewards() {
        rewards.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("daily-rewards.rewards");
        if (section == null) {
            // Add a sensible global default if none configured
            rewards.add(new DailyReward(DailyReward.GLOBAL, 50L, 100.0, List.of(),
                    "§a✦ Daily reward claimed! §7(+50 XP, +$100)"));
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            if (r == null) continue;
            rewards.add(new DailyReward(
                    key,
                    r.getLong("xp",     50L),
                    r.getDouble("money",100.0),
                    r.getStringList("commands"),
                    r.getString("message", "§a✦ Daily reward claimed!")
            ));
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** @return true if this system is enabled in config. */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("daily-rewards.enabled", true);
    }

    /**
     * Attempt to give a player their daily reward.
     *
     * @return true if the reward was granted; false if on cooldown or disabled.
     */
    public boolean claimReward(Player player) {
        if (!isEnabled()) {
            player.sendMessage("§c[RankForge] Daily rewards are disabled.");
            return false;
        }
        if (!canClaim(player)) {
            long remaining = remainingSeconds(player);
            player.sendMessage("§c[RankForge] Daily reward is on cooldown. §7Time left: §e"
                    + formatTime(remaining));
            return false;
        }

        DailyReward reward = findReward(player);

        // Fire cancellable event
        DailyRewardClaimEvent event = new DailyRewardClaimEvent(player, reward);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        applyReward(player, event.getReward());
        markClaimed(player);
        return true;
    }

    /** @return true if the player can claim their daily reward now. */
    public boolean canClaim(Player player) {
        long lastClaim = yaml.getLong("claims." + player.getUniqueId(), 0L);
        long cooldownMs = plugin.getConfig().getLong("daily-rewards.cooldown-hours", 24L) * 3_600_000L;
        return System.currentTimeMillis() - lastClaim >= cooldownMs;
    }

    /** Remaining cooldown in seconds (0 if claimable). */
    public long remainingSeconds(Player player) {
        long lastClaim = yaml.getLong("claims." + player.getUniqueId(), 0L);
        long cooldownMs = plugin.getConfig().getLong("daily-rewards.cooldown-hours", 24L) * 3_600_000L;
        long diff = cooldownMs - (System.currentTimeMillis() - lastClaim);
        return Math.max(0L, diff / 1000L);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private DailyReward findReward(Player player) {
        String rankId = getRankId(player);
        // Prefer rank-specific reward, fall back to global
        for (DailyReward r : rewards) {
            if (r.getRankId().equalsIgnoreCase(rankId)) return r;
        }
        for (DailyReward r : rewards) {
            if (r.isGlobal()) return r;
        }
        return new DailyReward(DailyReward.GLOBAL, 50L, 100.0, List.of(), "§aDaily reward claimed!");
    }

    private void applyReward(Player player, DailyReward reward) {
        // XP
        if (reward.getXp() > 0 && plugin.getExperienceManager() != null) {
            plugin.getExperienceManager().award(player, reward.getXp());
        }
        // Money
        if (reward.getMoney() > 0 && plugin.getSoftDependency().hasVault()) {
            plugin.getSoftDependency().getVaultEconomy()
                  .depositPlayer(player, reward.getMoney());
        }
        // Commands
        for (String raw : reward.getCommands()) {
            String cmd = raw.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        // Message
        if (!reward.getMessage().isBlank()) {
            player.sendMessage(reward.getMessage().replace("&", "§"));
        }
    }

    private synchronized void markClaimed(Player player) {
        yaml.set("claims." + player.getUniqueId(), System.currentTimeMillis());
        try { yaml.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().warning("[DailyReward] Failed to save daily-rewards.yml");
        }
    }

    private String getRankId(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        var data  = cache.get(player.getUniqueId());
        return data != null ? data.rankId() : plugin.getRankManager().getDefaultRankId();
    }

    private String formatTime(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format("%dh %dm %ds", h, m, s);
    }

    public List<DailyReward> getRewards() { return List.copyOf(rewards); }
}
