package com.joshuaop.rankforge.api.requirement;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.entity.Player;

/**
 * Interface for custom rank requirements provided by third-party plugins.
 *
 * <h3>Registration:</h3>
 * <pre>{@code
 * // In your plugin's onEnable():
 * RankForgeAPI api = RankForgeAPI.getInstance();
 * api.getCustomRequirementRegistry().register("playtime", new PlaytimeRequirement());
 * }</pre>
 *
 * <h3>ranks.yml usage:</h3>
 * <pre>
 * requirements:
 *   custom:
 *     playtime: 3600    # value passed to check() as the "configValue"
 * </pre>
 *
 * <h3>Implementation example:</h3>
 * <pre>{@code
 * public class PlaytimeRequirement implements CustomRequirement {
 *     public boolean check(Player player, RankModel rank, String configValue) {
 *         long required = Long.parseLong(configValue);
 *         return getPlaytime(player) >= required;
 *     }
 *
 *     public String getUnmetMessage(Player player, RankModel rank, String configValue) {
 *         return "§7Playtime: §c" + configValue + "s §8(have " + getPlaytime(player) + "s)";
 *     }
 *
 *     public String getTypeId() { return "playtime"; }
 * }
 * }</pre>
 */
public interface CustomRequirement {

    /**
     * Unique type identifier used in ranks.yml under {@code requirements.custom}.
     * Must be lowercase, no spaces (e.g. {@code "playtime"}, {@code "kills"}).
     */
    String getTypeId();

    /**
     * Returns true if the player satisfies this requirement for the given rank.
     *
     * @param player      the player being checked
     * @param rank        the rank the player is trying to attain
     * @param configValue the raw value from ranks.yml (e.g. {@code "3600"})
     */
    boolean check(Player player, RankModel rank, String configValue);

    /**
     * Returns a human-readable message shown when the requirement is not met.
     * Should follow the pattern {@code "§7Label: §c<required> §8(have <current>)"}.
     *
     * @param player      the player being checked
     * @param rank        the target rank
     * @param configValue raw config value
     */
    String getUnmetMessage(Player player, RankModel rank, String configValue);

    /**
     * Optional: called when the requirement is successfully met and the rank-up
     * is being applied (e.g. to deduct a resource).
     * Default implementation does nothing.
     */
    default void onSuccess(Player player, RankModel rank, String configValue) {}
}
