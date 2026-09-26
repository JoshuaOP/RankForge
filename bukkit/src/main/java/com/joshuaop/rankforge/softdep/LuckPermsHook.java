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

/**
 * Isolated LuckPerms integration.
 * All references to the LuckPerms API are contained here so the JVM only
 * loads this class (and the LuckPerms API classes) after we have confirmed
 * that LuckPerms is installed.
 * Never import or reference this class from code that runs before the
 * LuckPerms presence check — use DependencyManager / SoftDependency instead.
 */
class LuckPermsHook {

    private static final NodeMetadataKey<String> MANAGED =
            NodeMetadataKey.of("rankforge-managed", String.class);
    private final LuckPerms api;

    private LuckPermsHook(LuckPerms api) {
        this.api = api;
    }

    /**
     * Attempt to obtain the LuckPerms service provider and wrap it.
     *
     * @return a ready {@link LuckPermsHook}, or {@code null} if LuckPerms is not available.
     */
    static LuckPermsHook create(JavaPlugin plugin) {
        RegisteredServiceProvider<LuckPerms> rsp =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (rsp == null) return null;
        LuckPerms lp = rsp.getProvider();
        return lp != null ? new LuckPermsHook(lp) : null;
    }

    CompletableFuture<Void> applyPermissions(Player player, RankModel model) {
        UUID uuid = player.getUniqueId();
        return api.getUserManager().modifyUser(uuid, user -> {
            // Only remove nodes marked as ours. Other plugins' identical
            // permission nodes remain untouched.
            user.data().clear(node -> node.getMetadata(MANAGED)
                    .map("true"::equals).orElse(false));
            if (model == null) return;
            for (String perm : model.getPermissions()) {
                user.data().add(Node.builder(perm).value(true)
                        .withMetadata(MANAGED, "true").build());
            }
        });
    }

    CompletableFuture<Void> removePermissions(Player player, RankModel model) {
        UUID uuid = player.getUniqueId();
        return api.getUserManager().modifyUser(uuid, user -> {
            user.data().clear(node -> node.getMetadata(MANAGED)
                    .map("true"::equals).orElse(false));
        });
    }
}
