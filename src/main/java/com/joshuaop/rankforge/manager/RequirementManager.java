package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.requirement.CustomRequirement;
import com.joshuaop.rankforge.api.requirement.CustomRequirementRegistry;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Evaluates whether a player meets all requirements for a given rank.
 *
 * <p>Checks the following requirement types in order:
 * <ol>
 *   <li><b>Money</b>         — Vault balance via {@link com.joshuaop.rankforge.softdep.SoftDependency}.</li>
 *   <li><b>XP Level</b>      — Player's Minecraft XP level.</li>
 *   <li><b>Permission</b>    — A Bukkit permission node.</li>
 *   <li><b>Custom</b>        — Any registered {@link CustomRequirement} implementations that
 *                              have a per-rank value mapped via {@link #addRankRequirement}.</li>
 * </ol>
 *
 * <p>Custom requirements can be added by third-party plugins via:
 * <pre>{@code
 * RankForgeAPI.getInstance().getCustomRequirementRegistry()
 *     .register("kills", new KillCountRequirement());
 *
 * // Then map a value to a specific rank:
 * plugin.getRequirementManager().addRankRequirement("VIP", "kills", "100");
 * }</pre>
 */
public class RequirementManager {

    private final RankForge plugin;

    /**
     * Per-rank custom requirement values.
     * Structure: rankId → { typeId → configValue }
     * Populated by third-party plugins via {@link #addRankRequirement}.
     */
    private final Map<String, Map<String, String>> rankCustomRequirements = new HashMap<>();

    public RequirementManager(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /** @return true if the player satisfies every requirement of the given rank. */
    public boolean meetsAll(Player player, String rankId) {
        return getUnmet(player, rankId).isEmpty();
    }

    /**
     * @return list of human-readable unmet requirements; empty if all met.
     */
    public List<String> getUnmet(Player player, String rankId) {
        List<String> unmet = new ArrayList<>();
        RankModel rank = plugin.getRankManager().getRankData(rankId);
        if (rank == null) return unmet;

        // ── Built-in: Money ───────────────────────────────────────────────────
        double money = rank.getRequiredMoney();
        if (money > 0) {
            double balance = plugin.getSoftDependency().getBalance(player);
            if (balance < money)
                unmet.add("§7Money: §c$" + (long) money + " §8(have $" + (long) balance + ")");
        }

        // ── Built-in: XP Level ────────────────────────────────────────────────
        int xpLevel = rank.getRequiredXpLevel();
        if (xpLevel > 0 && player.getLevel() < xpLevel)
            unmet.add("§7XP Level: §c" + xpLevel + " §8(have " + player.getLevel() + ")");

        // ── Built-in: Permission ──────────────────────────────────────────────
        String perm = rank.getRequiredPermission();
        if (perm != null && !perm.isBlank() && !player.hasPermission(perm))
            unmet.add("§7Permission: §c" + perm);

        // ── Custom Requirements ───────────────────────────────────────────────
        CustomRequirementRegistry registry = plugin.getCustomRequirementRegistry();
        if (registry != null && !registry.getAll().isEmpty()) {
            Map<String, String> rankReqs = rankCustomRequirements.get(rankId.toLowerCase());
            if (rankReqs != null) {
                for (Map.Entry<String, String> entry : rankReqs.entrySet()) {
                    CustomRequirement req = registry.get(entry.getKey());
                    if (req == null) continue;
                    try {
                        if (!req.check(player, rank, entry.getValue())) {
                            unmet.add(req.getUnmetMessage(player, rank, entry.getValue()));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Requirements] CustomRequirement '"
                                + entry.getKey() + "' threw exception: " + e.getMessage());
                    }
                }
            }
        }

        return unmet;
    }

    /** Withdraw rank-up cost from the player via Vault (if money is configured). */
    public boolean withdrawMoney(Player player, double amount) {
        return plugin.getSoftDependency().withdraw(player, amount);
    }

    // ── Custom Requirement Configuration ─────────────────────────────────────

    /**
     * Associate a custom requirement value with a specific rank.
     * Called by third-party plugins to configure per-rank custom requirements
     * without modifying ranks.yml.
     *
     * <pre>{@code
     * // Example: VIP rank requires 100 kills
     * requirementManager.addRankRequirement("VIP", "kills", "100");
     * }</pre>
     *
     * @param rankId      the rank ID to configure (case-insensitive)
     * @param typeId      the custom requirement type ID (must be registered)
     * @param configValue the value passed to {@link CustomRequirement#check}
     */
    public void addRankRequirement(String rankId, String typeId, String configValue) {
        rankCustomRequirements
                .computeIfAbsent(rankId.toLowerCase(), k -> new LinkedHashMap<>())
                .put(typeId.toLowerCase(), configValue);
    }

    /**
     * Remove a custom requirement from a specific rank.
     *
     * @return true if the requirement was present and removed
     */
    public boolean removeRankRequirement(String rankId, String typeId) {
        Map<String, String> reqs = rankCustomRequirements.get(rankId.toLowerCase());
        if (reqs == null) return false;
        return reqs.remove(typeId.toLowerCase()) != null;
    }

    /**
     * Remove all custom requirements for a specific rank.
     */
    public void clearRankRequirements(String rankId) {
        rankCustomRequirements.remove(rankId.toLowerCase());
    }

    /**
     * Returns an unmodifiable view of custom requirement mappings for a rank.
     * Returns an empty map if none are configured.
     */
    public Map<String, String> getRankCustomRequirements(String rankId) {
        return Collections.unmodifiableMap(
                rankCustomRequirements.getOrDefault(rankId.toLowerCase(), Map.of()));
    }
}
