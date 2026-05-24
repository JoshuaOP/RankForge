package com.joshuaop.rankforge.api.requirement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for third-party {@link CustomRequirement} implementations.
 *
 * <p>Access via {@code RankForgeAPI.getInstance().getCustomRequirementRegistry()}.
 * The {@link com.joshuaop.rankforge.manager.RequirementManager} queries this
 * registry when evaluating a rank's {@code requirements.custom} section.
 *
 * <p>Registrations should be done in {@code Plugin#onEnable()} after RankForge
 * has loaded, or using RankForge's {@link com.joshuaop.rankforge.api.hook.HookRegistry}.
 *
 * <h3>Thread safety:</h3>
 * All mutating operations are synchronized. Reads are safe from any thread.
 */
public class CustomRequirementRegistry {

    private final Map<String, CustomRequirement> registry = new LinkedHashMap<>();

    /**
     * Register a new custom requirement type.
     *
     * @param typeId      must match {@link CustomRequirement#getTypeId()}
     * @param requirement the implementation to register
     * @throws IllegalArgumentException if typeId is null or blank
     * @throws IllegalStateException    if a requirement with this typeId is already registered
     */
    public synchronized void register(String typeId, CustomRequirement requirement) {
        if (typeId == null || typeId.isBlank())
            throw new IllegalArgumentException("CustomRequirement typeId must not be blank.");
        if (registry.containsKey(typeId.toLowerCase()))
            throw new IllegalStateException("A CustomRequirement with typeId '" + typeId + "' is already registered.");
        registry.put(typeId.toLowerCase(), requirement);
    }

    /**
     * Unregister a custom requirement by type ID.
     * Typically called in {@code Plugin#onDisable()}.
     *
     * @return true if a registration was removed
     */
    public synchronized boolean unregister(String typeId) {
        return registry.remove(typeId.toLowerCase()) != null;
    }

    /**
     * Returns the registered implementation for the given type ID, or {@code null}.
     */
    public CustomRequirement get(String typeId) {
        return typeId != null ? registry.get(typeId.toLowerCase()) : null;
    }

    /** Returns true if a requirement with this typeId is registered. */
    public boolean isRegistered(String typeId) {
        return typeId != null && registry.containsKey(typeId.toLowerCase());
    }

    /** Returns an unmodifiable view of all registered requirements. */
    public Collection<CustomRequirement> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /** Returns the number of registered custom requirements. */
    public int size() { return registry.size(); }
}
