package com.joshuaop.rankforge.softdep;

import com.joshuaop.rankforge.rank.RankModel;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.metadata.NodeMetadataKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Isolated LuckPerms integration.
 * All references to the LuckPerms API are contained here so the JVM only
 * loads this class (and the LuckPerms API classes) after we have confirmed
 * that LuckPerms is installed.
 */
class LuckPermsHook {

    private static final NodeMetadataKey<String> MANAGED =
            NodeMetadataKey.of("rankforge-managed", String.class);
    
    private final JavaPlugin plugin;
    private final LuckPerms api;

    private LuckPermsHook(JavaPlugin plugin, LuckPerms api) {
        this.plugin = plugin;
        this.api = api;
    }

    /**
     * Attempt to obtain the LuckPerms service provider and wrap it.
     *
     * @return a ready {@link LuckPermsHook}, or {@code null} if LuckPerms is not available.
     */
    static LuckPermsHook create(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null ||
                !plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            return null;
        }

        RegisteredServiceProvider<LuckPerms> rsp =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (rsp == null) return null;
        LuckPerms lp = rsp.getProvider();
        return lp != null ? new LuckPermsHook(plugin, lp) : null;
    }

    CompletableFuture<Void> applyPermissions(Player player, RankModel model) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        UUID uuid = player.getUniqueId();
        return api.getUserManager().modifyUser(uuid, user -> {
            // Only remove nodes marked as ours. Other plugins' identical
            // permission nodes remain untouched.
            user.data().clear(node -> node.getMetadata(MANAGED)
                    .map("true"::equals).orElse(false));
            
            if (model == null || model.getPermissions() == null) return;
            
            for (String perm : model.getPermissions()) {
                if (perm == null || perm.trim().isEmpty()) continue;
                user.data().add(Node.builder(perm.trim()).value(true)
                        .withMetadata(MANAGED, "true").build());
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to apply LuckPerms permissions for " + player.getName(), ex);
            return null;
        });
    }

    CompletableFuture<Void> removePermissions(Player player, RankModel model) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = player.getUniqueId();
        return api.getUserManager().modifyUser(uuid, user -> {
            user.data().clear(node -> node.getMetadata(MANAGED)
                    .map("true"::equals).orElse(false));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to remove LuckPerms permissions for " + player.getName(), ex);
            return null;
        });
    }
}
