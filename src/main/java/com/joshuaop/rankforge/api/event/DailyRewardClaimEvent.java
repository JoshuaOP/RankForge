package com.joshuaop.rankforge.api.event;

import com.joshuaop.rankforge.reward.DailyReward;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player successfully claims their daily reward.
 *
 * <p>This event is <b>cancellable</b>. Cancelling prevents the reward from
 * being granted but still marks the cooldown as used.
 */
public class DailyRewardClaimEvent extends RankForgeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player      player;
    private       DailyReward reward;
    private       boolean     cancelled = false;

    public DailyRewardClaimEvent(Player player, DailyReward reward) {
        this.player = player;
        this.reward = reward;
    }

    @Override public HandlerList getHandlers()          { return HANDLERS; }
    public  static HandlerList  getHandlerList()        { return HANDLERS; }

    /** The player claiming the reward. */
    public Player getPlayer() { return player; }

    /** The reward that will be given. May be replaced via {@link #setReward(DailyReward)}. */
    public DailyReward getReward() { return reward; }

    /**
     * Replace the reward that will be applied.
     * Useful for rank-based reward overrides in third-party plugins.
     */
    public void setReward(DailyReward reward) { this.reward = reward; }

    @Override public boolean isCancelled()           { return cancelled; }
    @Override public void    setCancelled(boolean v) { this.cancelled = v; }
}
