package com.joshuaop.rankforge.softdep;

import com.joshuaop.rankforge.rank.RankModel;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Isolated LuckPerms integration.
 * Kept in its own class so it is only loaded by the JVM when LuckPerms is present.
 * Never import or instantiate this class directly — use SoftDependency.
 */
class LuckPermsHook {

    private final LuckPerms api;

    LuckPermsHook(LuckPerms api) {
        this.api = api;
    }

    /** Grant all permissions defined in the rank model to the player. */
    void applyPermissions(Player player, RankModel model) {
        if (model == null || model.getPermissions().isEmpty()) return;
        UUID uuid = player.getUniqueId();
        api.getUserManager().modifyUser(uuid, user -> {
            for (String perm : model.getPermissions()) {
                user.data().add(Node.builder(perm).value(true).build());
            }
        });
    }

    /** Remove all permissions defined in the rank model from the player. */
    void removePermissions(Player player, RankModel model) {
        if (model == null || model.getPermissions().isEmpty()) return;
        UUID uuid = player.getUniqueId();
        api.getUserManager().modifyUser(uuid, user -> {
            for (String perm : model.getPermissions()) {
                user.data().remove(Node.builder(perm).value(true).build());
            }
        });
    }

    LuckPerms getApi() {
        return api;
    }
}
