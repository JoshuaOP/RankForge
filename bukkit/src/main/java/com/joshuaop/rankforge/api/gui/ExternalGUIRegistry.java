package com.joshuaop.rankforge.api.gui;

import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Registry for {@link ExternalGUIProvider} implementations.
 *
 * <p>Access via {@code RankForgeAPI.getInstance().getExternalGUIRegistry()}.
 *
 * <p>Only one provider can be registered per {@link ExternalGUIProvider.GuiType}.
 * Registering a second provider for the same type replaces the first.
 *
 * <h3>Thread safety:</h3>
 * All mutating operations are synchronized.
 */
public class ExternalGUIRegistry {

    private final Map<ExternalGUIProvider.GuiType, ExternalGUIProvider> registry
            = new EnumMap<>(ExternalGUIProvider.GuiType.class);
    private final Logger logger;

    public ExternalGUIRegistry(Logger logger) {
        this.logger = logger;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Register an external GUI provider for the given GUI type.
     * Replaces any existing provider for the same type.
     *
     * @param type     the GUI type to override
     * @param provider the provider implementation
     */
    public synchronized void register(ExternalGUIProvider.GuiType type, ExternalGUIProvider provider) {
        ExternalGUIProvider old = registry.put(type, provider);
        if (old != null) {
            logger.info("[API] External GUI replaced for " + type.name()
                    + ": " + old.getName() + " → " + provider.getName());
        } else {
            logger.info("[API] External GUI registered for " + type.name()
                    + ": " + provider.getName());
        }
    }

    /**
     * Remove any registered provider for the given GUI type.
     *
     * @return true if a provider was removed
     */
    public synchronized boolean unregister(ExternalGUIProvider.GuiType type) {
        return registry.remove(type) != null;
    }

    /** Remove all registered providers. */
    public synchronized void clear() { registry.clear(); }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns the provider for the given GUI type, or empty if none registered.
     */
    public Optional<ExternalGUIProvider> get(ExternalGUIProvider.GuiType type) {
        return Optional.ofNullable(registry.get(type));
    }

    /** Returns true if a custom provider is registered for the given type. */
    public boolean hasProvider(ExternalGUIProvider.GuiType type) {
        return registry.containsKey(type);
    }

    // ── Open Helpers ──────────────────────────────────────────────────────────

    /**
     * Open the custom GUI for the given type if a provider is registered.
     * Falls back gracefully if none is registered (caller handles the default GUI).
     *
     * @param type   the GUI type to open
     * @param player the player to show the GUI to
     * @return true if a custom provider handled the open; false → use built-in GUI
     */
    public boolean tryOpen(ExternalGUIProvider.GuiType type, Player player) {
        ExternalGUIProvider provider = registry.get(type);
        if (provider == null) return false;
        try {
            var inv = provider.buildInventory(player);
            if (inv == null) return false;
            player.openInventory(inv);
            provider.onOpen(player);
            return true;
        } catch (Exception e) {
            logger.warning("[API] ExternalGUIProvider '" + provider.getName()
                    + "' threw exception for " + type.name() + ": " + e.getMessage());
            return false;
        }
    }
}
