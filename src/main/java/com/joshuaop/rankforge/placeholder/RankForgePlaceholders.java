package com.joshuaop.rankforge.placeholder;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.ProgressService;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.rank.RankModel;
import com.joshuaop.rankforge.util.FormatUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PlaceholderAPI expansion — registers all %rankforge_xxx% placeholders.
 *
 * Progress placeholders use the fully-updated {@link ProgressService} which
 * accounts for ALL active requirement types.
 */
public class RankForgePlaceholders extends PlaceholderExpansion {

    private final RankForge plugin;

    public RankForgePlaceholders(RankForge plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "rankforge"; }
    @Override public @NotNull String getAuthor()     { return "JoshuaOP"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer op, @NotNull String params) {
        // System-level placeholders (no player context required)
        switch (params) {
            case "version"   -> { return plugin.getDescription().getVersion(); }
            case "gui_title" -> { return "§8✦ §6RankForge §8✦"; }
        }

        if (op == null) return null;

        try {
            PlayerData data  = loadData(op);
            String     curId = data != null ? data.rankId() : plugin.getRankManager().getDefaultRankId();
            RankModel  cur   = plugin.getRankManager().getRank(curId);
            RankModel  next  = plugin.getRankManager().getRank(plugin.getRankManager().getNextRankId(curId));

            // Pull online player instance for dynamically calculated live metrics
            Player p = op.getPlayer();

            ProgressService svc      = plugin.getApi().getProgressService();
            double          progress = p != null ? svc.getPercent(p) : 0.0;
            double          balance  = safeBalance(op);
            double          nextCost = next != null ? next.getRequiredMoney() : 0;

            return switch (params) {
                case "rank"                 -> curId;
                case "rank_name", "rank_display" -> cur != null ? cur.getDisplayName() : curId;
                case "rank_prefix"          -> cur  != null ? cur.getChatPrefix()   : "";
                case "rank_position"        -> String.valueOf(getRankPosition(curId));
                case "is_max_rank"          -> String.valueOf(next == null);
                case "next_rank"            -> next != null ? next.getId()          : "MAX";
                case "next_cost"            -> next != null ? String.format("$%,.0f", next.getRequiredMoney()) : "MAX";
                case "cost"                 -> cur  != null ? String.format("$%,.0f", cur.getRequiredMoney())  : "0";
                case "progress"             -> String.format("%.1f", progress);
                case "progress_percent"     -> String.format("%.1f", progress) + "%";
                case "progress_bar"         -> p != null ? svc.getProgressBar(p) : "----------";
                case "required_progress"    -> next != null ? formatReq(next)      : "§6MAX";
                case "remaining_progress"   -> calculateRemainingProgress(op, next, balance);
                case "money"                -> String.format("$%,.0f", balance);
                case "has_money"            -> String.valueOf(nextCost == 0 || balance >= nextCost);
                case "missing_money"        -> next != null ? String.format("$%,.0f", Math.max(0, next.getRequiredMoney() - balance)) : "0";
                case "requirements_status"  -> p != null ? requirementStatus(p, next) : "§7Offline";
                case "requirements_detail"  -> p != null ? requirementDetail(p, next) : "§7Offline";
                case "xp_level"             -> p != null ? String.valueOf(p.getLevel()) : "0";
                case "xp_progress"          -> p != null ? (p.getExp() >= 0.99f ? "99.9%" : String.format("%.1f", p.getExp() * 100f) + "%") : "0.0%";
                case "player"               -> op.getName() != null ? op.getName() : "Unknown";
                case "uuid"                 -> op.getUniqueId().toString();
                case "lang"                 -> plugin.getLangManager().getPlayerLang(op.getUniqueId());
                default                     -> null;
            };
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[Placeholders] Exception for placeholder '"
                        + params + "' on " + op.getName() + ": " + e.getMessage());
            }
            return "";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlayerData loadData(OfflinePlayer op) {
        try {
            var cache = plugin.getRankManager().getCacheManager();
            if (cache.contains(op.getUniqueId())) {
                PlayerData data = cache.get(op.getUniqueId());
                if (plugin.getRankManager().getRank(data.rankId()) == null) {
                    data = data.withRank(plugin.getRankManager().getDefaultRankId());
                }
                return data;
            }
            return PlayerData.defaultData(op.getUniqueId(), op.getName(), plugin.getRankManager().getDefaultRankId());
        } catch (Exception e) {
            return null;
        }
    }

    private double safeBalance(OfflinePlayer op) {
        try {
            if (plugin.getSoftDependency() == null || !plugin.getSoftDependency().hasVault()) return 0.0;
            Player online = op.getPlayer();
            if (online != null) return plugin.getSoftDependency().getBalance(online);
            return plugin.getSoftDependency().getBalance(op);
        } catch (Exception ignored) {}
        return 0.0;
    }

    private int getRankPosition(String rankId) {
        int pos = 1;
        for (String id : plugin.getRankManager().getRankIds()) {
            if (id.equals(rankId)) return pos;
            pos++;
        }
        return pos;
    }

    private String formatReq(RankModel next) {
        StringBuilder sb = new StringBuilder();
        if (next.getRequiredMoney()  > 0) sb.append("$").append(String.format("%,.0f", next.getRequiredMoney())).append(" ");
        if (next.getRequiredXpLevel() > 0) sb.append("Lv").append(next.getRequiredXpLevel()).append(" ");
        if (next.getRequiredBlockBreaks() > 0) sb.append(next.getRequiredBlockBreaks()).append(" blocks ");
        if (next.getRequiredMobKills() > 0) sb.append(next.getRequiredMobKills()).append(" kills ");
        if (next.getRequiredPlaytimeMinutes() > 0) sb.append(FormatUtil.formatTime(next.getRequiredPlaytimeMinutes())).append(" ");
        String result = sb.toString().trim();
        return result.isEmpty() ? "§7None" : result;
    }

    private String calculateRemainingProgress(OfflinePlayer op, RankModel next, double balance) {
        if (next == null) return "§6MAX";
        StringBuilder sb = new StringBuilder();

        double moneyLeft = next.getRequiredMoney() - balance;
        if (moneyLeft > 0) sb.append("$").append(String.format("%,.0f", moneyLeft)).append(" ");

        Player p = op.getPlayer();
        if (p != null) {
            int xpLeft = next.getRequiredXpLevel() - p.getLevel();
            if (xpLeft > 0) sb.append("Lv").append(xpLeft);
        } else if (next.getRequiredXpLevel() > 0) {
            sb.append("Lv").append(next.getRequiredXpLevel());
        }

        if (plugin.getBlockBreakTracker() != null && next.getRequiredBlockBreaks() > 0) {
            long haveBlocks = plugin.getBlockBreakTracker().getCount(op.getUniqueId());
            long leftBlocks = next.getRequiredBlockBreaks() - haveBlocks;
            if (leftBlocks > 0) sb.append(" ").append(String.format("%,d", leftBlocks)).append(" blocks");
        }
        return sb.length() > 0 ? sb.toString().trim() : "§aMet";
    }

    private String requirementStatus(Player p, RankModel next) {
        if (next == null) return "§6MAX RANK";
        try {
            boolean met = plugin.getRequirementManager().meetsAll(p, next.getId());
            return met ? "§a✔ Requirements Met" : "§c✘ Requirements Unmet";
        } catch (Exception e) {
            return "§7Unknown";
        }
    }

    private String requirementDetail(Player p, RankModel next) {
        if (next == null) return "§6MAX RANK";
        try {
            List<ProgressService.RequirementProgress> reqs = plugin.getApi().getProgressService().getRequirementProgress(p, next);
            if (reqs.isEmpty()) return "§a✔ No requirements";
            StringBuilder sb = new StringBuilder();
            for (ProgressService.RequirementProgress rp : reqs) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(rp.toDisplayLine());
            }
            return sb.toString();
        } catch (Exception e) {
            return "§7Unavailable";
        }
    }
}
