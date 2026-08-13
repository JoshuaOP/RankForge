package com.joshuaop.rankforge.api.hook;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

/**
 * Registry for {@link PluginHook} implementations.
 *
 * <p>Access via {@code RankForgeAPI.getInstance().getHookRegistry()}.
 * RankForge's internal systems call the fire* methods at appropriate points.
 *
 * <p>Hooks are called <em>after</em> the corresponding Bukkit events have been
 * fired and processed, guaranteeing that rank data is fully committed before
 * hook callbacks are invoked.
 *
 * <h3>Thread safety:</h3>
 * All mutating operations are synchronized. Fire methods are safe to call
 * from the main server thread.
 */
public class HookRegistry {

    private final List<PluginHook> hooks  = new ArrayList<>();
    private final Logger           logger;

    public HookRegistry(Logger logger) {
        this.logger = logger;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Register a plugin hook. Hooks are called in registration order.
     *
     * @param hook the hook to register
     */
    public synchronized void register(PluginHook hook) {
        hooks.add(hook);
        logger.info("[API] Hook registered: " + hook.getPluginName());
    }

    /**
     * Unregister all hooks registered by the given plugin name.
     *
     * @param pluginName the {@link PluginHook#getPluginName()} value
     * @return number of hooks removed
     */
    public synchronized int unregister(String pluginName) {
        int before = hooks.size();
        hooks.removeIf(h -> h.getPluginName().equalsIgnoreCase(pluginName));
        return before - hooks.size();
    }

    /** Remove all registered hooks. */
    public synchronized void clear() { hooks.clear(); }

    // ── Fire ──────────────────────────────────────────────────────────────────

    /** Invoke {@link PluginHook#onRankup} on all registered hooks. */
    public void fireRankup(Player player, String oldRankId, String newRankId) {
        for (PluginHook hook : snapshot()) {
            try { hook.onRankup(player, oldRankId, newRankId); }
            catch (Exception e) {
                logger.warning("[API] Hook '" + hook.getPluginName()
                        + "' threw exception on onRankup: " + e.getMessage());
            }
        }
    }

    /** Invoke {@link PluginHook#onRankSet} on all registered hooks. */
    public void fireRankSet(Player player, String oldRankId, String newRankId) {
        for (PluginHook hook : snapshot()) {
            try { hook.onRankSet(player, oldRankId, newRankId); }
            catch (Exception e) {
                logger.warning("[API] Hook '" + hook.getPluginName()
                        + "' threw exception on onRankSet: " + e.getMessage());
            }
        }
    }

    /** Invoke {@link PluginHook#onRankReset} on all registered hooks. */
    public void fireRankReset(Player player, String oldRankId) {
        for (PluginHook hook : snapshot()) {
            try { hook.onRankReset(player, oldRankId); }
            catch (Exception e) {
                logger.warning("[API] Hook '" + hook.getPluginName()
                        + "' threw exception on onRankReset: " + e.getMessage());
            }
        }
    }

    /** Invoke {@link PluginHook#onPlayerLoad} on all registered hooks. */
    public void firePlayerLoad(Player player, String rankId) {
        for (PluginHook hook : snapshot()) {
            try { hook.onPlayerLoad(player, rankId); }
            catch (Exception e) {
                logger.warning("[API] Hook '" + hook.getPluginName()
                        + "' threw exception on onPlayerLoad: " + e.getMessage());
            }
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** Returns an unmodifiable view of registered hooks. */
    public List<PluginHook> getAll() {
        return Collections.unmodifiableList(hooks);
    }

    public int size() { return hooks.size(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private synchronized List<PluginHook> snapshot() {
        return List.copyOf(hooks);
    }
}
