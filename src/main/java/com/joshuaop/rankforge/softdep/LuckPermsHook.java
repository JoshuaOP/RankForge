package com.joshuaop.rankforge.softdep;

import com.joshuaop.rankforge.rank.RankModel;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Isolated LuckPerms integration.
 * All references to the LuckPerms API are contained here so the JVM only
 * loads this class (and the LuckPerms API classes) after we have confirmed
 * that LuckPerms is installed.
 * Never import or reference this class from code that runs before the
 * LuckPerms presence check — use DependencyManager / SoftDependency instead.
 */
class LuckPermsHook {

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

    void applyPermissions(Player player, RankModel model) {
        if (model == null || model.getPermissions().isEmpty()) return;
        UUID uuid = player.getUniqueId();
        api.getUserManager().modifyUser(uuid, user -> {
            for (String perm : model.getPermissions()) {
                user.data().add(Node.builder(perm).value(true).build());
            }
        });
    }

    void removePermissions(Player player, RankModel model) {
        if (model == null || model.getPermissions().isEmpty()) return;
        UUID uuid = player.getUniqueId();
        api.getUserManager().modifyUser(uuid, user -> {
            for (String perm : model.getPermissions()) {
                user.data().remove(Node.builder(perm).value(true).build());
            }
        });
    }
}
