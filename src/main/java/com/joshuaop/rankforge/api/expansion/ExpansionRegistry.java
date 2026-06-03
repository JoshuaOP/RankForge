package com.joshuaop.rankforge.api.expansion;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Registry and lifecycle manager for {@link RankForgeExpansion} instances.
 *
 * <p>Access via {@code RankForgeAPI.getInstance().getExpansionRegistry()}.
 *
 * <p>On plugin disable or hot-reload, RankForge calls {@link #disableAll()} /
 * {@link #reloadAll()} automatically so external expansions are properly
 * notified without manual tracking.
 *
 * <h3>Thread safety:</h3>
 * All mutating operations are synchronized.
 */
public class ExpansionRegistry {

    private final Map<String, RankForgeExpansion> registry = new LinkedHashMap<>();
    private final Logger                           logger;

    public ExpansionRegistry(Logger logger) {
        this.logger = logger;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Register and enable an expansion.
     *
     * @param expansion the expansion to register
     * @throws IllegalStateException if an expansion with the same name is already registered
     */
    public synchronized void register(RankForgeExpansion expansion) {
        String key = expansion.getName().toLowerCase();
        if (registry.containsKey(key))
            throw new IllegalStateException("Expansion '" + expansion.getName() + "' is already registered.");
        registry.put(key, expansion);
        try {
            expansion.onEnable();
            logger.info("[API] Expansion registered: " + expansion);
        } catch (Exception e) {
            logger.warning("[API] Failed to enable expansion '" + expansion.getName() + "': " + e.getMessage());
            registry.remove(key);
        }
    }

    /**
     * Disable and unregister an expansion by name.
     *
     * @param name case-insensitive expansion name
     * @return true if removed
     */
    public synchronized boolean unregister(String name) {
        RankForgeExpansion expansion = registry.remove(name.toLowerCase());
        if (expansion == null) return false;
        try { expansion.onDisable(); }
        catch (Exception e) {
            logger.warning("[API] Error disabling expansion '" + name + "': " + e.getMessage());
        }
        return true;
    }

    /** Disable all registered expansions. Called on RankForge shutdown. */
    public synchronized void disableAll() {
        for (RankForgeExpansion exp : registry.values()) {
            try { exp.onDisable(); }
            catch (Exception e) {
                logger.warning("[API] Error disabling expansion '" + exp.getName() + "': " + e.getMessage());
            }
        }
        registry.clear();
    }

    /** Hot-reload all registered expansions. Called on {@code /rank reload}. */
    public synchronized void reloadAll() {
        for (RankForgeExpansion exp : registry.values()) {
            try { exp.onReload(); }
            catch (Exception e) {
                logger.warning("[API] Error reloading expansion '" + exp.getName() + "': " + e.getMessage());
            }
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** Returns the expansion with the given name, or {@code null}. */
    public RankForgeExpansion get(String name) {
        return name != null ? registry.get(name.toLowerCase()) : null;
    }

    /** Returns true if an expansion with this name is registered. */
    public boolean isRegistered(String name) {
        return name != null && registry.containsKey(name.toLowerCase());
    }

    /** Unmodifiable view of all registered expansions. */
    public Collection<RankForgeExpansion> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /** Number of currently registered expansions. */
    public int size() { return registry.size(); }
}
