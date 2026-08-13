package com.joshuaop.rankforge.api.event;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when an admin uses {@code /rank set <player> <rank>} or
 * {@link com.joshuaop.rankforge.api.RankForgeAPI#setRank(Player, String)}.
 *
 * <p>This event is <b>cancellable</b> — cancelling it prevents the rank from
 * being applied, but does not send any feedback to the command sender automatically.
 */
public class RankSetEvent extends RankForgeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player        target;
    private final CommandSender setter;
    private final String        oldRankId;
    private final String        newRankId;
    private       boolean       cancelled = false;

    public RankSetEvent(Player target, CommandSender setter, String oldRankId, String newRankId) {
        this.target    = target;
        this.setter    = setter;
        this.oldRankId = oldRankId;
        this.newRankId = newRankId;
    }

    @Override public HandlerList getHandlers()          { return HANDLERS; }
    public  static HandlerList  getHandlerList()        { return HANDLERS; }

    /** The player whose rank is being set. */
    public Player        getTarget()    { return target; }

    /** The command sender who issued the set (may be console). */
    public CommandSender getSetter()    { return setter; }

    /** The rank ID before the change. */
    public String        getOldRankId() { return oldRankId; }

    /** The rank ID being applied. */
    public String        getNewRankId() { return newRankId; }

    @Override public boolean isCancelled()           { return cancelled; }
    @Override public void    setCancelled(boolean v) { this.cancelled = v; }
}
