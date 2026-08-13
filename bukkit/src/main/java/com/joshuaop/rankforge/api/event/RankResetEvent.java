package com.joshuaop.rankforge.api.event;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player's rank is reset to the server default via
 * {@code /rank reset <player>} or
 * {@link com.joshuaop.rankforge.api.RankForgeAPI#resetRank(Player)}.
 *
 * <p>This event is <b>cancellable</b>.
 */
public class RankResetEvent extends RankForgeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player        target;
    private final CommandSender resetter;
    private final String        oldRankId;
    private final String        defaultRankId;
    private       boolean       cancelled = false;

    public RankResetEvent(Player target, CommandSender resetter,
                          String oldRankId, String defaultRankId) {
        this.target        = target;
        this.resetter      = resetter;
        this.oldRankId     = oldRankId;
        this.defaultRankId = defaultRankId;
    }

    @Override public HandlerList getHandlers()          { return HANDLERS; }
    public  static HandlerList  getHandlerList()        { return HANDLERS; }

    /** The player being reset. */
    public Player        getTarget()        { return target; }

    /** Who triggered the reset (may be console). */
    public CommandSender getResetter()      { return resetter; }

    /** Rank before the reset. */
    public String        getOldRankId()     { return oldRankId; }

    /** The default rank the player will be moved to. */
    public String        getDefaultRankId() { return defaultRankId; }

    @Override public boolean isCancelled()           { return cancelled; }
    @Override public void    setCancelled(boolean v) { this.cancelled = v; }
}
