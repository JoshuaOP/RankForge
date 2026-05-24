package com.joshuaop.rankforge.permission;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.logging.Level;

/**
 * Programmatically registers all RankForge permission nodes with the Bukkit plugin manager.
 * This runs at startup so permissions are available even before plugin.yml is fully parsed.
 */
public class PermissionNodeGenerator {

    private final RankForge plugin;

    public PermissionNodeGenerator(RankForge plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all static permission nodes and one dynamic node per configured rank.
     */
    public void generateAll() {
        int count = 0;

        for (String node : PermissionRegistry.ALL_NODES) {
            if (register(node, PermissionDefault.FALSE)) count++;
        }

        register(PermissionRegistry.STAR, PermissionDefault.OP);

        for (String rankId : plugin.getRankManager().getRankIds()) {
            String node = PermissionRegistry.BASE + ".rank." + rankId.toLowerCase();
            if (register(node, PermissionDefault.FALSE)) count++;
        }

        plugin.getLogger().info("[Perms] Registered " + count + " permission nodes.");
    }

    /**
     * Register a single permission node.
     * Returns false if the node was already registered (no-op, safe to call twice).
     */
    private boolean register(String node, PermissionDefault def) {
        if (plugin.getServer().getPluginManager().getPermission(node) != null) return false;
        try {
            plugin.getServer().getPluginManager()
                    .addPermission(new Permission(node, def));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Could not register permission: " + node, e);
            return false;
        }
    }
}
