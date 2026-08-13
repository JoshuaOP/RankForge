package com.joshuaop.rankforge.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player is about to rank up via {@code /rank up} or the API.
 *
 * <p>This event is <b>cancellable</b>. If cancelled, the rank-up is aborted
 * and the player's rank remains unchanged.
 *
 * <h3>Usage example:</h3>
 * <pre>{@code
 * @EventHandler
 * public void onRankup(RankupEvent event) {
 *     if (event.getNewRankId().equals("VIP")) {
 *         event.getPlayer().sendMessage("Welcome to VIP!");
 *     }
 * }
 * }</pre>
 */
public class RankupEvent extends RankForgeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String oldRankId;
    private final String newRankId;
    private       boolean cancelled = false;

    public RankupEvent(Player player, String oldRankId, String newRankId) {
        this.player    = player;
        this.oldRankId = oldRankId;
        this.newRankId = newRankId;
    }

    // ── Bukkit event boilerplate ──────────────────────────────────────────────

    @Override public HandlerList getHandlers()           { return HANDLERS; }
    public  static HandlerList  getHandlerList()         { return HANDLERS; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** The player who is ranking up. */
    public Player getPlayer()    { return player; }

    /** The rank ID the player is ranking up <em>from</em>. */
    public String getOldRankId() { return oldRankId; }

    /** The rank ID the player is ranking up <em>to</em>. */
    public String getNewRankId() { return newRankId; }

    @Override public boolean isCancelled()            { return cancelled; }
    @Override public void    setCancelled(boolean v)  { this.cancelled = v; }
}
