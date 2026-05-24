package com.joshuaop.rankforge.softdep;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.rank.RankModel;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
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
 * Handles player data loading on join and cosmetic/permission restoration.
 *
 * Note: LuckPerms API classes are only referenced inside LuckPermsHook so the
 * JVM does not load them on servers without LuckPerms installed.
 */
public class SoftDependency implements Listener {

    private final RankForge plugin;

    private Economy       vaultEconomy;
    private LuckPermsHook luckPermsHook;
    private boolean       papiEnabled;

    public SoftDependency(RankForge plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        setupVault();
        setupLuckPerms();
        checkPapi();
    }

    // ── Player Event Hooks ────────────────────────────────────────────────────

    /**
     * On player join:
     *  1. Ensure player data is in cache (from startup load or defaults for new players).
     *  2. Apply LuckPerms rank permissions (or Bukkit attachment fallback).
     *  3. Restore cosmetic effects (particle trail, tablist prefix).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        CacheManager cache = plugin.getRankManager().getCacheManager();

        // If not already loaded by startup YAML sync, create default entry
        if (!cache.contains(uuid)) {
            cache.put(uuid, PlayerData.defaultData(
                    uuid,
                    player.getName(),
                    plugin.getRankManager().getDefaultRankId()));
        } else {
            // Refresh stored player name in case it changed
            PlayerData existing = cache.get(uuid);
            if (!existing.playerName().equals(player.getName())) {
                cache.put(uuid, new PlayerData(
                        uuid, player.getName(), existing.rankId(),
                        existing.experience(), existing.money(), existing.language()));
            }
        }

        String rankId = cache.get(uuid).rankId();

        // Apply rank permissions via LuckPerms (or Bukkit attachment fallback)
        applyRankPermissions(player, rankId);

        // Restore cosmetics (particle trail, tablist prefix)
        plugin.getCosmeticManager().onLogin(player, rankId);

        // PAPI: no special action needed — placeholders resolve dynamically
    }

    /**
     * On player quit:
     *  1. Remove cosmetic effects cleanly.
     *  2. If using YAML storage, immediately persist this player's data.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // Remove cosmetics before the player object becomes invalid
        plugin.getCosmeticManager().onLogout(player);

        // Persist data immediately to YAML when not using MySQL
        if (!plugin.getDatabaseManager().isConnected()) {
            CacheManager cache = plugin.getRankManager().getCacheManager();
            if (cache.contains(uuid)) {
                plugin.getTaskScheduler().async(() ->
                        plugin.getYamlPlayerDataStorage()
                              .saveAll(java.util.List.of(cache.get(uuid))));
            }
        }
    }

    // ── Vault ─────────────────────────────────────────────────────────────────

    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            vaultEconomy = rsp.getProvider();
            plugin.getLogger().info("[SoftDep] Vault economy hooked.");
        }
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

    // ── LuckPerms ─────────────────────────────────────────────────────────────

    /**
     * Only instantiates LuckPermsHook when LuckPerms is confirmed present.
     * Keeps that class off the JVM's load path on servers without LuckPerms.
     */
    private void setupLuckPerms() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) return;
        try {
            RegisteredServiceProvider<LuckPerms> rsp =
                    plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (rsp != null) {
                luckPermsHook = new LuckPermsHook(rsp.getProvider());
                plugin.getLogger().info("[SoftDep] LuckPerms hooked.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SoftDep] LuckPerms hook failed: " + e.getMessage());
        }
    }

    /**
     * Apply rank permissions to a player.
     * Uses LuckPerms if available, otherwise falls back to Bukkit PermissionAttachment.
     */
    public void applyRankPermissions(Player player, String rankId) {
        RankModel model = plugin.getRankManager().getRankData(rankId);
        if (model == null || model.getPermissions().isEmpty()) return;

        if (luckPermsHook != null) {
            luckPermsHook.applyPermissions(player, model);
        } else {
            for (String perm : model.getPermissions()) {
                player.addAttachment(plugin, perm, true);
            }
        }
    }

    /**
     * Remove all LuckPerms nodes for a given rank from a player.
     * Used if a rank is changed so stale permissions are cleared before new ones are applied.
     */
    public void removeRankPermissions(Player player, String rankId) {
        if (luckPermsHook == null) return;
        RankModel model = plugin.getRankManager().getRankData(rankId);
        if (model == null || model.getPermissions().isEmpty()) return;
        luckPermsHook.removePermissions(player, model);
    }

    // ── PlaceholderAPI ────────────────────────────────────────────────────────

    private void checkPapi() {
        papiEnabled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papiEnabled) plugin.getLogger().info("[SoftDep] PlaceholderAPI detected.");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean   hasVault()        { return vaultEconomy != null; }
    public boolean   hasLuckPerms()    { return luckPermsHook != null; }
    public boolean   hasPapi()         { return papiEnabled; }
    public LuckPerms getLuckPerms()    { return luckPermsHook != null ? luckPermsHook.getApi() : null; }
    public Economy   getVaultEconomy() { return vaultEconomy; }
}
