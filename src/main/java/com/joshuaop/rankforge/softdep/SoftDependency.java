package com.joshuaop.rankforge.softdep;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.rank.RankModel;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

/**
 * Unified soft-dependency handler and player event listener.
 * Manages Vault, LuckPerms, and PlaceholderAPI hooks.
 */
public class SoftDependency implements Listener {

    private final RankForge plugin;

    private Economy       vaultEconomy;
    private LuckPermsHook luckPermsHook;
    private boolean       papiEnabled;
    private boolean       floodgateEnabled;

    public SoftDependency(RankForge plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        setupVault();
        setupLuckPerms();
        checkPapi();
        checkFloodgate();

        plugin.getLogger().info("[SoftDep] Vault=" + (vaultEconomy != null ? "✓" : "✗")
                + "  LuckPerms=" + (luckPermsHook != null ? "✓" : "✗")
                + "  PlaceholderAPI=" + (papiEnabled ? "✓" : "✗")
                + "  Floodgate=" + (floodgateEnabled ? "✓" : "✗"));
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

        // Validate the player's stored rank still exists; repair to default if not.
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

            // Stitch live Vault balance before persisting so the saved value stays current.
            if (data != null && vaultEconomy != null) {
                try {
                    double liveBalance = vaultEconomy.getBalance(player);
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
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) vaultEconomy = rsp.getProvider();
    }

    public double getBalance(Player player) {
        if (vaultEconomy == null) return 0;
        try { return vaultEconomy.getBalance(player); }
        catch (Exception e) { return 0; }
    }

    public boolean withdraw(Player player, double amount) {
        if (vaultEconomy == null) return false;
        try {
            return vaultEconomy.has(player, amount)
                    && vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
        } catch (Exception e) { return false; }
    }

    /**
     * Set an offline/online player's Vault balance to an exact amount.
     * Since Vault has no direct "set balance" API, this calculates the delta and
     * deposits or withdraws accordingly.
     */
    public void setBalance(OfflinePlayer player, double targetAmount) {
        if (vaultEconomy == null) return;
        try {
            double current = vaultEconomy.getBalance(player);
            double diff    = targetAmount - current;
            if (diff > 0)      vaultEconomy.depositPlayer(player, diff);
            else if (diff < 0) vaultEconomy.withdrawPlayer(player, -diff);
        } catch (Exception ignored) {}
    }

    // ── LuckPerms ─────────────────────────────────────────────────────────────

    private void setupLuckPerms() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) return;
        try {
            RegisteredServiceProvider<LuckPerms> rsp =
                    plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (rsp != null) luckPermsHook = new LuckPermsHook(rsp.getProvider());
        } catch (Exception e) {
            plugin.getLogger().warning("[SoftDep] LuckPerms hook failed: " + e.getMessage());
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
    }

    // ── Floodgate / Geyser Crossplay ──────────────────────────────────────────

    private void checkFloodgate() {
        floodgateEnabled = plugin.getServer().getPluginManager().getPlugin("floodgate") != null
                || plugin.getServer().getPluginManager().getPlugin("Floodgate") != null;
    }

    /**
     * Returns {@code true} if the player is connecting via Geyser/Floodgate (Bedrock Edition).
     *
     * <p>Detection strategy:
     * <ol>
     *   <li>Check if the Floodgate plugin is present and the player name starts with the
     *       configured Bedrock prefix (default {@code "."}).</li>
     *   <li>Fall back to checking the plain username prefix alone if Floodgate isn't loaded,
     *       so that crossplay-safe branches still degrade gracefully on Java-only servers.</li>
     * </ol>
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

    public boolean   hasVault()           { return vaultEconomy != null; }
    public boolean   hasLuckPerms()       { return luckPermsHook != null; }
    public boolean   hasPapi()            { return papiEnabled; }
    public boolean   hasFloodgate()       { return floodgateEnabled; }
    public LuckPerms getLuckPerms()       { return luckPermsHook != null ? luckPermsHook.getApi() : null; }
    public Economy   getVaultEconomy()    { return vaultEconomy; }
}
