package com.joshuaop.rankforge.api.hook;

/**
 * Interface for third-party plugin hooks that integrate with RankForge.
 *
 * <p>A hook is a lightweight bridge between RankForge and another plugin.
 * Unlike a full {@link com.joshuaop.rankforge.api.expansion.RankForgeExpansion},
 * a hook does not have lifecycle methods — it is a pure callback contract.
 *
 * <h3>Common use cases:</h3>
 * <ul>
 *   <li>Notify an external plugin when a player ranks up.</li>
 *   <li>Bridge RankForge rank data to another stats system.</li>
 *   <li>Override the rank-up sound or visual effect.</li>
 * </ul>
 *
 * <h3>Registration:</h3>
 * <pre>{@code
 * RankForgeAPI.getInstance().getHookRegistry()
 *     .register(new MyPluginHook());
 * }</pre>
 *
 * <h3>Implementation example:</h3>
 * <pre>{@code
 * public class MyPluginHook implements PluginHook {
 *     public String getPluginName() { return "MyPlugin"; }
 *
 *     public void onRankup(Player player, String oldRankId, String newRankId) {
 *         MyPlugin.getStatsManager().recordRankup(player);
 *     }
 * }
 * }</pre>
 */
public interface PluginHook {

    /**
     * The name of the plugin this hook bridges to.
     * Used for identification and logging only.
     */
    String getPluginName();

    /**
     * Called immediately after a player successfully ranks up.
     * Executes on the main server thread.
     *
     * @param player     the player who ranked up (Bukkit Player)
     * @param oldRankId  the rank the player ranked up from
     * @param newRankId  the rank the player ranked up to
     */
    default void onRankup(org.bukkit.entity.Player player, String oldRankId, String newRankId) {}

    /**
     * Called immediately after an admin sets a player's rank.
     *
     * @param player    the affected player
     * @param oldRankId previous rank
     * @param newRankId new rank
     */
    default void onRankSet(org.bukkit.entity.Player player, String oldRankId, String newRankId) {}

    /**
     * Called immediately after a player's rank is reset to the default.
     *
     * @param player    the affected player
     * @param oldRankId rank before the reset
     */
    default void onRankReset(org.bukkit.entity.Player player, String oldRankId) {}

    /**
     * Called when a player joins and their rank data is loaded into the cache.
     * Useful for synchronising rank data to external systems.
     *
     * @param player  the joining player
     * @param rankId  the player's current rank ID
     */
    default void onPlayerLoad(org.bukkit.entity.Player player, String rankId) {}
}
