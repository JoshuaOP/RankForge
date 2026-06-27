package com.joshuaop.rankforge.softdep;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Unified soft-dependency handler and player event listener.
 *
 * <p>All optional API classes (LuckPerms, Vault Economy, PlaceholderAPI, Floodgate)
 * are intentionally NOT imported at the class level.  Each dependency is isolated
 * inside its own adapter ({@link LuckPermsHook}, {@link VaultAdapter}) which the JVM
 * only loads after we confirm the corresponding plugin is installed.  This prevents
 * {@code NoClassDefFoundError} / {@code ClassNotFoundException} when optional plugins
 * are absent.</p>
 */
public class SoftDependency implements Listener {

    private final RankForge plugin;

    private VaultAdapter   vaultAdapter;
    private LuckPermsHook  luckPermsHook;
    private boolean        papiEnabled;
    private boolean        floodgateEnabled;

    public SoftDependency(RankForge plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        setupVault();
        setupLuckPerms();
        checkPapi();
        checkFloodgate();
    }

    // ── Player Events ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        PlayerData data = plugin.getRankManager().getRepository().load(uuid, player.getName());

        if (!data.playerName().equals(player.getName())) {
            data = data.withPlayerName(player.getName());
            plugin.getRankManager().getCacheManager().put(uuid, data);
        }

        if (plugin.getRankManager().getRank(data.rankId()) == null) {
            String fallback = plugin.getRankManager().getDefaultRankId();
            data = data.withRank(fallback);
            plugin.getRankManager().getCacheManager().put(uuid, data);
            if (plugin.isDebug()) plugin.getLogger().info(
                    "[SoftDep] Repaired orphaned rank for " + player.getName() + " → '" + fallback + "'");
        }

        applyRankPermissions(player, data.rankId());
        plugin.getCosmeticManager().onLogin(player, data.rankId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        plugin.getCosmeticManager().onLogout(player);

        CacheManager cache = plugin.getRankManager().getCacheManager();
        if (cache.contains(uuid)) {
            PlayerData data = cache.get(uuid);

            if (data != null && vaultAdapter != null) {
                try {
                    double liveBalance = vaultAdapter.getBalance(player);
                    data = data.withMoney(liveBalance);
                    cache.put(uuid, data);
                } catch (Exception ignored) {}
            }

            final PlayerData toSave = data;
            plugin.getTaskScheduler().async(() ->
                    plugin.getRankManager().getRepository().save(toSave));

            cache.scheduleCleanup(uuid);
        }
    }

    // ── Vault ─────────────────────────────────────────────────────────────────

    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("[RankForge] Vault not found. Economy features disabled.");
            return;
        }
        try {
            vaultAdapter = VaultAdapter.create(plugin);
            if (vaultAdapter != null) {
                plugin.getLogger().info("[RankForge] \u2713 Vault integration enabled.");
            } else {
                plugin.getLogger().warning("[RankForge] Vault found but no Economy provider is registered.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[RankForge] Vault hook failed: " + e.getMessage());
        }
    }

    public double getBalance(Player player) {
        if (vaultAdapter == null) return 0;
        return vaultAdapter.getBalance(player);
    }

    public double getBalance(OfflinePlayer player) {
        if (vaultAdapter == null) return 0;
        return vaultAdapter.getBalance(player);
    }

    public boolean withdraw(Player player, double amount) {
        if (vaultAdapter == null) return false;
        return vaultAdapter.withdraw(player, amount);
    }

    /**
     * Set an offline/online player's Vault balance to an exact amount.
     */
    public void setBalance(OfflinePlayer player, double targetAmount) {
        if (vaultAdapter == null) return;
        vaultAdapter.setBalance(player, targetAmount);
    }

    // ── LuckPerms ─────────────────────────────────────────────────────────────

    private void setupLuckPerms() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("[RankForge] LuckPerms not found. Permission integration disabled.");
            return;
        }
        try {
            luckPermsHook = LuckPermsHook.create(plugin);
            if (luckPermsHook != null) {
                plugin.getLogger().info("[RankForge] \u2713 LuckPerms integration enabled.");
            } else {
                plugin.getLogger().warning("[RankForge] LuckPerms found but service provider is unavailable.");
            }
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("[SoftDep-Debug] LuckPerms hook failed: " + e.getMessage());
            }
        }
    }

    public void applyRankPermissions(Player player, String rankId) {
        RankModel model = plugin.getRankManager().getRank(rankId);
        if (model == null || model.getPermissions().isEmpty()) return;

        if (luckPermsHook != null) {
            luckPermsHook.applyPermissions(player, model);
        } else {
            for (String perm : model.getPermissions()) {
                player.addAttachment(plugin, perm, true);
            }
        }
    }

    public void removeRankPermissions(Player player, String rankId) {
        if (luckPermsHook == null) return;
        RankModel model = plugin.getRankManager().getRank(rankId);
        if (model == null || model.getPermissions().isEmpty()) return;
        luckPermsHook.removePermissions(player, model);
    }

    // ── PlaceholderAPI ────────────────────────────────────────────────────────

    private void checkPapi() {
        papiEnabled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papiEnabled) {
            plugin.getLogger().info("[RankForge] \u2713 PlaceholderAPI integration enabled.");
        } else {
            plugin.getLogger().info("[RankForge] PlaceholderAPI not found. Placeholder support disabled.");
        }
    }

    // ── Floodgate / Geyser Crossplay ──────────────────────────────────────────

    private void checkFloodgate() {
        floodgateEnabled = plugin.getServer().getPluginManager().getPlugin("floodgate") != null
                || plugin.getServer().getPluginManager().getPlugin("Floodgate") != null;
        if (floodgateEnabled) {
            plugin.getLogger().info("[RankForge] \u2713 Floodgate integration enabled.");
        } else {
            plugin.getLogger().info("[RankForge] Floodgate not found. Bedrock support disabled.");
        }
    }

    /**
     * Returns {@code true} if the player is connecting via Geyser/Floodgate (Bedrock Edition).
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        String prefix = plugin.getConfig().getString("crossplay.bedrock-prefix", ".");
        return player.getName().startsWith(prefix);
    }

    /**
     * Returns a crossplay-safe display name by stripping the Bedrock prefix if present.
     * Example: {@code ".JoshuaBE"} → {@code "JoshuaBE"}.
     */
    public String getCleanName(Player player) {
        if (!isBedrockPlayer(player)) return player.getName();
        String prefix = plugin.getConfig().getString("crossplay.bedrock-prefix", ".");
        return player.getName().startsWith(prefix) ? player.getName().substring(prefix.length()) : player.getName();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean hasVault()       { return vaultAdapter != null; }
    public boolean hasLuckPerms()   { return luckPermsHook != null; }
    public boolean hasPapi()        { return papiEnabled; }
    public boolean hasFloodgate()   { return floodgateEnabled; }
}
