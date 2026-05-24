package com.joshuaop.rankforge.api.reward;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for third-party {@link CustomReward} implementations.
 *
 * <p>Access via {@code RankForgeAPI.getInstance().getCustomRewardRegistry()}.
 * The {@link com.joshuaop.rankforge.api.RankService} queries this registry
 * when executing rank-up rewards.
 *
 * <h3>Thread safety:</h3>
 * All mutating operations are synchronized. Reads are safe from any thread.
 */
public class CustomRewardRegistry {

    private final Map<String, CustomReward> registry = new LinkedHashMap<>();

    /**
     * Register a custom reward handler.
     *
     * @param typeId the unique type ID (must match {@link CustomReward#getTypeId()})
     * @param reward the implementation
     * @throws IllegalArgumentException if typeId is null or blank
     * @throws IllegalStateException    if typeId is already registered
     */
    public synchronized void register(String typeId, CustomReward reward) {
        if (typeId == null || typeId.isBlank())
            throw new IllegalArgumentException("CustomReward typeId must not be blank.");
        if (registry.containsKey(typeId.toLowerCase()))
            throw new IllegalStateException("A CustomReward with typeId '" + typeId + "' is already registered.");
        registry.put(typeId.toLowerCase(), reward);
    }

    /**
     * Unregister a custom reward.
     *
     * @return true if it was registered and has been removed
     */
    public synchronized boolean unregister(String typeId) {
        return registry.remove(typeId.toLowerCase()) != null;
    }

    /** Retrieve a registered reward by typeId, or {@code null} if not found. */
    public CustomReward get(String typeId) {
        return typeId != null ? registry.get(typeId.toLowerCase()) : null;
    }

    /** Returns true if a reward with this typeId is registered. */
    public boolean isRegistered(String typeId) {
        return typeId != null && registry.containsKey(typeId.toLowerCase());
    }

    /** Returns an unmodifiable view of all registered rewards. */
    public Collection<CustomReward> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /** Returns the number of registered custom rewards. */
    public int size() { return registry.size(); }
}
