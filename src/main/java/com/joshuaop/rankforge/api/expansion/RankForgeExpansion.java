package com.joshuaop.rankforge.api.expansion;

import com.joshuaop.rankforge.api.RankForgeAPI;

/**
 * Abstract base class for RankForge expansion modules.
 *
 * <p>An expansion is a self-contained add-on that integrates with RankForge
 * via the Developer API. Expansions are registered through the
 * {@link ExpansionRegistry} and managed by RankForge's lifecycle.
 *
 * <h3>Creating an expansion:</h3>
 * <pre>{@code
 * public class MyExpansion extends RankForgeExpansion {
 *
 *     public MyExpansion(RankForgeAPI api) { super(api); }
 *
 *     public String getName()    { return "MyExpansion"; }
 *     public String getVersion() { return "1.0"; }
 *     public String getAuthor()  { return "YourName"; }
 *
 *     public void onEnable() {
 *         // Register listeners, custom requirements, rewards, etc.
 *         api.getCustomRequirementRegistry().register("my_req", new MyRequirement());
 *     }
 *
 *     public void onDisable() {
 *         api.getCustomRequirementRegistry().unregister("my_req");
 *     }
 * }
 * }</pre>
 *
 * <h3>Registration:</h3>
 * <pre>{@code
 * // In your plugin's onEnable(), after RankForge loads:
 * RankForgeAPI api = RankForgeAPI.getInstance();
 * api.getExpansionRegistry().register(new MyExpansion(api));
 * }</pre>
 */
public abstract class RankForgeExpansion {

    /** The API reference available to all expansion implementations. */
    protected final RankForgeAPI api;

    protected RankForgeExpansion(RankForgeAPI api) {
        this.api = api;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Human-readable expansion name, e.g. {@code "PlaytimeRequirements"}. */
    public abstract String getName();

    /** Semantic version string, e.g. {@code "1.0.0"}. */
    public abstract String getVersion();

    /** Author or organisation name. */
    public abstract String getAuthor();

    /**
     * Optional: a short description of what this expansion provides.
     */
    public String getDescription() { return "A RankForge expansion."; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called by RankForge when the expansion is registered and enabled.
     * Register all custom requirements, rewards, listeners, etc. here.
     */
    public abstract void onEnable();

    /**
     * Called by RankForge when the expansion is disabled or the plugin unloads.
     * Clean up all registrations to avoid memory leaks.
     */
    public abstract void onDisable();

    /**
     * Called when RankForge performs a hot-reload.
     * Default implementation calls {@link #onDisable()} then {@link #onEnable()}.
     * Override for custom reload logic.
     */
    public void onReload() {
        onDisable();
        onEnable();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the underlying API instance. */
    public RankForgeAPI getAPI() { return api; }

    @Override
    public String toString() {
        return getName() + " v" + getVersion() + " by " + getAuthor();
    }
}
