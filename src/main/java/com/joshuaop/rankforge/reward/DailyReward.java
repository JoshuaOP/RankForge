package com.joshuaop.rankforge.reward;

import java.util.List;

/**
 * Immutable model representing a daily reward configuration.
 *
 * <p>Daily rewards can optionally be specific to a rank — if a player is on
 * the matching rank they receive that reward; otherwise the {@code global}
 * reward is used. Loaded from config.yml under {@code daily-rewards.rewards}.
 *
 * <p>A reward consists of:
 * <ul>
 *   <li>{@code xp}       — RankForge XP granted.</li>
 *   <li>{@code money}    — Vault money granted.</li>
 *   <li>{@code commands} — Console commands executed (supports {@code %player%}).</li>
 *   <li>{@code message}  — Chat message sent to the player (&amp; color codes).</li>
 * </ul>
 */
public final class DailyReward {

    /** Sentinel rank ID meaning this reward applies to all ranks. */
    public static final String GLOBAL = "global";

    private final String       rankId;
    private final long         xp;
    private final double       money;
    private final List<String> commands;
    private final String       message;

    public DailyReward(String rankId, long xp, double money,
                       List<String> commands, String message) {
        this.rankId   = rankId;
        this.xp       = xp;
        this.money    = money;
        this.commands = List.copyOf(commands);
        this.message  = message != null ? message : "";
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Rank ID this reward targets, or {@link #GLOBAL}. */
    public String       getRankId()   { return rankId; }
    public long         getXp()       { return xp; }
    public double       getMoney()    { return money; }
    public List<String> getCommands() { return commands; }
    public String       getMessage()  { return message; }

    public boolean isGlobal() { return GLOBAL.equalsIgnoreCase(rankId); }

    @Override
    public String toString() {
        return "DailyReward{rank='" + rankId + "', xp=" + xp + ", money=" + money + "}";
    }
}
