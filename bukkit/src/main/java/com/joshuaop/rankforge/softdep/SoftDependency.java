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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.permissions.PermissionAttachment;

/**
 * Unified soft-dependency handler and player event listener.
 *
 * <p>All optional API classes (LuckPerms, Vault Economy, PlaceholderAPI, Floodgate)
 * are intentionally NOT imported at the class level. Each dependency is isolated
 * inside its own adapter ({@link LuckPermsHook}, {@link VaultAdapter}) which the JVM
 * only loads after we confirm the corresponding plugin is installed.</p>
 */
public class SoftDependency implements Listener {

    private final RankForge plugin;

    private VaultAdapter   vaultAdapter;
    private LuckPermsHook  luckPermsHook;
    private boolean        papiEnabled;
    private boolean        floodgateEnabled;
    private final ConcurrentHashMap<UUID, PermissionAttachment> rankAttachments =
            new ConcurrentHashMap<>();

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

        if (data == null) {
            return; // Safety guard if loading fails entirely
        }

        if (!data.playerName().equals(player.getName())) {
            data = data.withPlayerName(player.getName());
            plugin.getRankManager().getCacheManager().put(uuid, data);
        }

        if (plugin.getRankManager().getRank(data.rankId()) == null) {
            String fallback = plugin.getRankManager().getDefaultRankId();
            data = data.withRank(fallback);
            plugin.getRankManager().getCacheManager().put(uuid, data);
            if (plugin.isDebug()) plugin.getLogger().info(
                    "Repaired orphaned rank for " + player.getName() + " → '" + fallback + "'");
        }

        if (plugin.getBypassRegistry() != null) {
            plugin.getBypassRegistry().loadPersisted(uuid, data.completedRequirements());
        }

        applyRankPermissions(player, null, data.rankId());
        plugin.getCosmeticManager().onLogin(player, data.rankId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // Clean up fallback local permission attachments if used
        PermissionAttachment attachment = rankAttachments.remove(uuid);
        if (attachment != null) {
            try {
                attachment.remove();
            } catch (Exception ignored) {}
        }

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
            plugin.getTaskScheduler().async(() -> {
                if (toSave != null && !plugin.getRankManager().getRepository().save(toSave)) {
                    plugin.getLogger().warning("Quit save was not confirmed for " + uuid + ".");
                }
            });

            cache.scheduleCleanup(uuid);
        }
    }

    // ── Vault ─────────────────────────────────────────────────────────────────

    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found. Economy features disabled.");
            return;
        }
        try {
            vaultAdapter = VaultAdapter.create(plugin);
            if (vaultAdapter != null) {
                plugin.getLogger().info("✔ Vault integration enabled.");
            } else {
                plugin.getLogger().warning("Vault found but no Economy provider is registered.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Vault hook failed: " + e.getMessage());
        }
    }

    public double getBalance(Player player) {
        if (vaultAdapter == null || player == null) return 0;
        return vaultAdapter.getBalance(player);
    }

    public double getBalance(OfflinePlayer player) {
        if (vaultAdapter == null || player == null) return 0;
        return vaultAdapter.getBalance(player);
    }

    public boolean withdraw(Player player, double amount) {
        if (vaultAdapter == null || player == null) return false;
        return vaultAdapter.withdraw(player, amount);
    }

    public boolean refund(Player player, double amount) {
        if (vaultAdapter == null || player == null) return false;
        return vaultAdapter.refund(player, amount);
    }

    public void setBalance(OfflinePlayer player, double targetAmount) {
        if (vaultAdapter == null || player == null) return;
        vaultAdapter.setBalance(player, targetAmount);
    }

    // ── LuckPerms ─────────────────────────────────────────────────────────────

    private void setupLuckPerms() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("LuckPerms not found. Permission integration disabled.");
            return;
        }
        try {
            luckPermsHook = LuckPermsHook.create(plugin);
            if (luckPermsHook != null) {
                plugin.getLogger().info("✔ LuckPerms integration enabled.");
            } else {
                plugin.getLogger().warning("LuckPerms found but service provider is unavailable.");
            }
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("LuckPerms hook failed: " + e.getMessage());
            }
        }
    }

    public void applyRankPermissions(Player player, String rankId) {
        applyRankPermissions(player, null, rankId);
    }

    public boolean applyRankPermissions(Player player, String oldRankId, String rankId) {
        if (player == null || !player.isOnline()) return false;
        RankModel model = plugin.getRankManager().getRank(rankId);

        if (luckPermsHook != null) {
            try {
                // Non-blocking async application with callback handling to prevent main thread freezing
                var operation = luckPermsHook.applyPermissions(player, model);
                if (operation == null) return false;
                
                operation.whenComplete((result, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING,
                                "LuckPerms rank permission update failed asynchronously for "
                                        + player.getUniqueId(), error);
                    }
                });
                return true;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not apply RankForge LuckPerms permissions for "
                                + player.getName(), e);
                return false;
            }
        } else {
            // Fallback native PermissionAttachments
            try {
                PermissionAttachment attachment = player.addAttachment(plugin);
                if (model != null && model.getPermissions() != null) {
                    for (String perm : model.getPermissions()) {
                        if (perm != null && !perm.isBlank()) {
                            attachment.setPermission(perm.trim(), true);
                        }
                    }
                }
                
                PermissionAttachment previous = rankAttachments.put(player.getUniqueId(), attachment);
                if (previous != null) {
                    try {
                        previous.remove();
                    } catch (Exception ignored) {}
                }
                return true;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not apply native RankForge permissions for " + player.getName(), e);
                return false;
            }
        }
    }

    public void removeRankPermissions(Player player, String rankId) {
        if (player == null) return;
        
        if (luckPermsHook != null) {
            try {
                var operation = luckPermsHook.removePermissions(
                        player, plugin.getRankManager().getRank(rankId));
                if (operation != null) {
                    operation.whenComplete((ignored, error) -> {
                        if (error != null) {
                            plugin.getLogger().log(Level.WARNING,
                                    "LuckPerms permission cleanup failed for "
                                            + player.getUniqueId(), error);
                        }
                    });
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not clean up LuckPerms permissions for "
                                + player.getName(), e);
            }
            return;
        }
        
        PermissionAttachment attachment = rankAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                attachment.remove();
            } catch (Exception ignored) {}
        }
    }

    // ── PlaceholderAPI ────────────────────────────────────────────────────────

    private void checkPapi() {
        papiEnabled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papiEnabled) {
            plugin.getLogger().info("✔ PlaceholderAPI integration enabled.");
        } else {
            plugin.getLogger().info("PlaceholderAPI not found. Placeholder support disabled.");
        }
    }

    // ── Floodgate / Geyser Crossplay ──────────────────────────────────────────

    private void checkFloodgate() {
        floodgateEnabled = plugin.getServer().getPluginManager().getPlugin("floodgate") != null
                || plugin.getServer().getPluginManager().getPlugin("Floodgate") != null;
        if (floodgateEnabled) {
            plugin.getLogger().info("✔ Floodgate integration enabled.");
        } else {
            plugin.getLogger().info("Floodgate not found. Bedrock support disabled.");
        }
    }

    public boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        String prefix = plugin.getConfig().getString("crossplay.bedrock-prefix", ".");
        return player.getName().startsWith(prefix);
    }

    public String getCleanName(Player player) {
        if (player == null) return "";
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
