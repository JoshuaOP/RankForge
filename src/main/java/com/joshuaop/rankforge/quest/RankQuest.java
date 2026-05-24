package com.joshuaop.rankforge.quest;

import java.util.List;

/**
 * Immutable model for a rank quest.
 *
 * <p>A quest is a multi-step challenge sequence. Players complete each step in
 * order; upon completing the final step the quest reward is granted. Quests
 * may be one-time or repeatable.
 *
 * <h3>quests.yml structure (one entry):</h3>
 * <pre>
 * quests:
 *   journey_to_elite:
 *     name: "Journey to Elite"
 *     description: "A multi-step challenge to reach Elite rank."
 *     rank-required: ""          # Leave blank for all ranks
 *     repeatable: false
 *     steps:
 *       - challenge: "daily_miner"   # Must be a challenge ID from challenges.yml
 *       - challenge: "mob_slayer"
 *     reward-xp: 1000
 *     reward-money: 5000.0
 *     reward-commands:
 *       - "give %player% emerald 5"
 *     reward-message: "§aYou completed the Journey to Elite quest!"
 * </pre>
 */
public final class RankQuest {

    /**
     * A single step within a quest — references a challenge ID.
     */
    public record Step(String challengeId) {}

    private final String       id;
    private final String       name;
    private final String       description;
    private final String       rankRequired;
    private final boolean      repeatable;
    private final List<Step>   steps;
    private final long         rewardXp;
    private final double       rewardMoney;
    private final List<String> rewardCommands;
    private final String       rewardMessage;

    public RankQuest(String id, String name, String description, String rankRequired,
                     boolean repeatable, List<Step> steps, long rewardXp, double rewardMoney,
                     List<String> rewardCommands, String rewardMessage) {
        this.id             = id;
        this.name           = name;
        this.description    = description;
        this.rankRequired   = rankRequired != null ? rankRequired : "";
        this.repeatable     = repeatable;
        this.steps          = List.copyOf(steps);
        this.rewardXp       = rewardXp;
        this.rewardMoney    = rewardMoney;
        this.rewardCommands = List.copyOf(rewardCommands);
        this.rewardMessage  = rewardMessage != null ? rewardMessage : "";
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String       getId()             { return id; }
    public String       getName()           { return name; }
    public String       getDescription()    { return description; }
    public String       getRankRequired()   { return rankRequired; }
    public boolean      isRepeatable()      { return repeatable; }
    public List<Step>   getSteps()          { return steps; }
    public long         getRewardXp()       { return rewardXp; }
    public double       getRewardMoney()    { return rewardMoney; }
    public List<String> getRewardCommands() { return rewardCommands; }
    public String       getRewardMessage()  { return rewardMessage; }
    public boolean      hasRankRequirement(){ return !rankRequired.isBlank(); }
    public int          getTotalSteps()     { return steps.size(); }

    @Override
    public String toString() {
        return "RankQuest{id='" + id + "', steps=" + steps.size() + "}";
    }
}
