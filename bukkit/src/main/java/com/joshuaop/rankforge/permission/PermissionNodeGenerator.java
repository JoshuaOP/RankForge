package com.joshuaop.rankforge.permission;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.logging.Level;

/**
 * Programmatically registers all RankForge permission nodes with the Bukkit plugin manager.
 * This runs at startup so permissions are available even before plugin.yml is fully parsed.
 *
 * <p>Defaults:
 * <ul>
 *   <li>All {@code rankforge.use.*} nodes — {@link PermissionDefault#TRUE}</li>
 *   <li>All {@code rankforge.admin.*} nodes — {@link PermissionDefault#OP}</li>
 *   <li>{@link PermissionRegistry#STAR} — {@link PermissionDefault#OP}</li>
 * </ul>
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
        // Player nodes — default true
        register(PermissionRegistry.USE_STAR,         PermissionDefault.TRUE);
        register(PermissionRegistry.USE,              PermissionDefault.TRUE);
        register(PermissionRegistry.USE_UP,           PermissionDefault.TRUE);
        register(PermissionRegistry.USE_CURRENT,      PermissionDefault.TRUE);
        register(PermissionRegistry.USE_NEXT,         PermissionDefault.TRUE);
        register(PermissionRegistry.USE_PROGRESS,     PermissionDefault.TRUE);
        register(PermissionRegistry.USE_REQUIREMENTS, PermissionDefault.TRUE);
        register(PermissionRegistry.USE_HISTORY,      PermissionDefault.TRUE);
        register(PermissionRegistry.USE_HELP,         PermissionDefault.TRUE);
        register(PermissionRegistry.USE_LANG,         PermissionDefault.TRUE);
        register(PermissionRegistry.USE_VERSION,      PermissionDefault.TRUE);
        register(PermissionRegistry.USE_XP,           PermissionDefault.TRUE);

        // Admin nodes — default op
        register(PermissionRegistry.ADMIN_STAR,        PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_RELOAD,      PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_EDITOR,      PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_PLAYER_LIST, PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_CREATE,      PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_DELETE,      PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_SET,         PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_FORCE,       PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_RESET,       PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_BYPASSREQ,   PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_STATS,       PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_DEBUG,       PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_SECURITY,    PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_SOUND,       PermissionDefault.OP);
        register(PermissionRegistry.ADMIN_XP,          PermissionDefault.OP);

        // Top-level wildcard
        register(PermissionRegistry.STAR, PermissionDefault.OP);

        // Dynamic per-rank permission nodes
        for (String rankId : plugin.getRankManager().getRankIds()) {
            String node = PermissionRegistry.BASE + ".rank." + rankId.toLowerCase();
            register(node, PermissionDefault.FALSE);
        }
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
