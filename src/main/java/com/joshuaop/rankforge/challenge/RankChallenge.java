package com.joshuaop.rankforge.challenge;

import java.util.List;

/**
 * Immutable model for a single rank challenge.
 *
 * <p>Challenges are objective-based tasks players complete for XP, money, or
 * console-command rewards. They are loaded from {@code challenges.yml} and
 * tracked per-player by {@link RankChallengeManager}.
 *
 * <h3>challenges.yml structure (one entry):</h3>
 * <pre>
 * challenges:
 *   daily_miner:
 *     name: "Daily Miner"
 *     description: "Mine 64 blocks"
 *     type: MINE_BLOCK
 *     target-count: 64
 *     reward-xp: 200
 *     reward-money: 0.0
 *     reward-commands: []
 *     rank-required: ""          # Leave blank for all ranks
 *     repeatable: true
 *     cooldown-hours: 24
 * </pre>
 */
public final class RankChallenge {

    /** Supported challenge objective types. */
    public enum ChallengeType {
        /** Player breaks a block (BlockBreakEvent). */
        MINE_BLOCK,
        /** Player kills a mob or player (EntityDeathEvent). */
        KILL_ENTITY,
        /** Player crafts an item (CraftItemEvent). */
        CRAFT_ITEM,
        /** Player ranks up N times. */
        RANKUP,
        /** Admin-awarded: manually progressed via API. */
        MANUAL
    }

    private final String            id;
    private final String            name;
    private final String            description;
    private final ChallengeType     type;
    private final int               targetCount;
    private final long              rewardXp;
    private final double            rewardMoney;
    private final List<String>      rewardCommands;
    private final String            rankRequired;
    private final boolean           repeatable;
    private final long              cooldownHours;

    public RankChallenge(String id, String name, String description, ChallengeType type,
                         int targetCount, long rewardXp, double rewardMoney,
                         List<String> rewardCommands, String rankRequired,
                         boolean repeatable, long cooldownHours) {
        this.id             = id;
        this.name           = name;
        this.description    = description;
        this.type           = type;
        this.targetCount    = targetCount;
        this.rewardXp       = rewardXp;
        this.rewardMoney    = rewardMoney;
        this.rewardCommands = List.copyOf(rewardCommands);
        this.rankRequired   = rankRequired != null ? rankRequired : "";
        this.repeatable     = repeatable;
        this.cooldownHours  = cooldownHours;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String            getId()             { return id; }
    public String            getName()           { return name; }
    public String            getDescription()    { return description; }
    public ChallengeType     getType()           { return type; }
    public int               getTargetCount()    { return targetCount; }
    public long              getRewardXp()       { return rewardXp; }
    public double            getRewardMoney()    { return rewardMoney; }
    public List<String>      getRewardCommands() { return rewardCommands; }
    public String            getRankRequired()   { return rankRequired; }
    public boolean           isRepeatable()      { return repeatable; }
    public long              getCooldownHours()  { return cooldownHours; }
    public boolean           hasRankRequirement(){ return !rankRequired.isBlank(); }

    @Override
    public String toString() {
        return "RankChallenge{id='" + id + "', type=" + type + ", target=" + targetCount + "}";
    }
}
