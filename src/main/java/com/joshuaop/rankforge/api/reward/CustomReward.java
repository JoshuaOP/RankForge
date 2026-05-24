package com.joshuaop.rankforge.api.reward;

import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.entity.Player;

/**
 * Interface for custom rank-up reward handlers provided by third-party plugins.
 *
 * <p>Custom rewards are applied <em>after</em> the built-in reward pipeline
 * (permissions, cosmetics, announcements) completes. They are registered via
 * {@link CustomRewardRegistry} and associated with specific ranks in ranks.yml
 * under {@code custom-rewards}.
 *
 * <h3>Registration:</h3>
 * <pre>{@code
 * RankForgeAPI.getInstance().getCustomRewardRegistry()
 *     .register("title_reward", new TitleReward());
 * }</pre>
 *
 * <h3>ranks.yml usage:</h3>
 * <pre>
 * ranks:
 *   VIP:
 *     custom-rewards:
 *       title_reward: "§6VIP"   # configValue passed to apply()
 * </pre>
 *
 * <h3>Implementation example:</h3>
 * <pre>{@code
 * public class TitleReward implements CustomReward {
 *     public String getTypeId() { return "title_reward"; }
 *
 *     public void apply(Player player, RankModel rank, String configValue) {
 *         player.sendTitle(configValue, "You ranked up!", 10, 60, 10);
 *     }
 * }
 * }</pre>
 */
public interface CustomReward {

    /**
     * Unique type identifier (lowercase, no spaces).
     * Used as the key in ranks.yml {@code custom-rewards} section.
     */
    String getTypeId();

    /**
     * Apply the reward to the player.
     *
     * @param player      the player who just ranked up
     * @param rank        the rank the player attained
     * @param configValue raw value from ranks.yml (may be empty if none provided)
     */
    void apply(Player player, RankModel rank, String configValue);

    /**
     * Optional: return a log-friendly description of this reward for debugging.
     */
    default String describe(RankModel rank, String configValue) {
        return getTypeId() + "[" + configValue + "] for rank=" + rank.getId();
    }
}
