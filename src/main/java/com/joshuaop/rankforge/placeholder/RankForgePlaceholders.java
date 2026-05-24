package com.joshuaop.rankforge.placeholder;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.rank.RankModel;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PlaceholderAPI expansion — registers all %rankforge_xxx% placeholders.
 *
 * Full list:
 *   %rankforge_rank%                %rankforge_rank_name%
 *   %rankforge_next_rank%           %rankforge_progress%
 *   %rankforge_progress_bar%        %rankforge_progress_percent%
 *   %rankforge_required_progress%   %rankforge_remaining_progress%
 *   %rankforge_rank_display%        %rankforge_rank_position%
 *   %rankforge_is_max_rank%         %rankforge_cost%
 *   %rankforge_money%               %rankforge_has_money%
 *   %rankforge_missing_money%       %rankforge_player%
 *   %rankforge_uuid%                %rankforge_lang%
 *   %rankforge_version%             %rankforge_gui_title%
 *   %rankforge_top_rank_1/2/3%
 *   %rankforge_rank_prefix%         %rankforge_next_cost%
 *   %rankforge_requirements_status%
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
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer.getPlayer();

        // System placeholders (no player required)
        return switch (params) {
            case "version"   -> plugin.getDescription().getVersion();
            case "gui_title" -> "§8✦ §6RankForge §8✦";
            case "top_rank_1"-> getTopRank(0);
            case "top_rank_2"-> getTopRank(1);
            case "top_rank_3"-> getTopRank(2);
            default          -> player != null ? onPlayer(player, params) : null;
        };
    }

    private String onPlayer(Player p, String params) {
        PlayerData data  = loadData(p);
        String     curId = data.rankId();
        RankModel  cur   = plugin.getRankManager().getRankData(curId);
        RankModel  next  = plugin.getRankManager().getRankData(
                plugin.getRankManager().getNextRankId(curId));
        double progress  = plugin.getApi().getProgress(p);
        double balance   = plugin.getSoftDependency().getBalance(p);
        double nextCost  = next != null ? next.getRequiredMoney() : 0;

        return switch (params) {
            case "rank"                -> curId;
            case "rank_name"           -> cur != null ? cur.getDisplayName() : curId;
            case "rank_display"        -> cur != null ? cur.getDisplayName() : curId;
            case "rank_prefix"         -> cur != null ? cur.getChatPrefix() : "";
            case "rank_position"       -> String.valueOf(getRankPosition(curId));
            case "is_max_rank"         -> String.valueOf(next == null);
            case "next_rank"           -> next != null ? next.getId() : "MAX";
            case "next_cost"           -> next != null ? String.format("$%,.0f", next.getRequiredMoney()) : "MAX";
            case "cost"                -> cur  != null ? String.format("$%,.0f", cur.getRequiredMoney()) : "0";
            case "progress"            -> String.format("%.1f", progress);
            case "progress_percent"    -> String.format("%.1f", progress) + "%";
            case "progress_bar"        -> plugin.getApi().getProgressService().getProgressBar(p);
            case "required_progress"   -> next != null ? formatReq(next) : "§6MAX";
            case "remaining_progress"  -> remaining(next, balance, p.getLevel());
            case "money"               -> String.format("$%,.0f", balance);
            case "has_money"           -> String.valueOf(nextCost == 0 || balance >= nextCost);
            case "missing_money"       -> next != null ? String.format("$%,.0f",
                    Math.max(0, next.getRequiredMoney() - balance)) : "0";
            case "requirements_status" -> requirementStatus(p, next);
            case "player"              -> p.getName();
            case "uuid"                -> p.getUniqueId().toString();
            case "lang"                -> plugin.getLangManager().getPlayerLang(p.getUniqueId());
            default                    -> null;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlayerData loadData(Player p) {
        var cache = plugin.getRankManager().getCacheManager();
        if (cache.contains(p.getUniqueId())) return cache.get(p.getUniqueId());
        return PlayerData.defaultData(p.getUniqueId(), p.getName(),
                plugin.getRankManager().getDefaultRankId());
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
        if (next.getRequiredMoney() > 0) sb.append("$").append(String.format("%,.0f", next.getRequiredMoney())).append(" ");
        if (next.getRequiredXpLevel() > 0) sb.append("Lv").append(next.getRequiredXpLevel()).append(" ");
        return sb.toString().trim();
    }

    private String remaining(RankModel next, double balance, int level) {
        if (next == null) return "§6MAX";
        StringBuilder sb = new StringBuilder();
        double moneyLeft = next.getRequiredMoney() - balance;
        int    xpLeft    = next.getRequiredXpLevel() - level;
        if (moneyLeft > 0) sb.append("$").append(String.format("%,.0f", moneyLeft)).append(" ");
        if (xpLeft    > 0) sb.append("Lv").append(xpLeft);
        return sb.length() > 0 ? sb.toString().trim() : "§aMet";
    }

    private String requirementStatus(Player p, RankModel next) {
        if (next == null) return "§6MAX RANK";
        boolean met = plugin.getRequirementManager().meetsAll(p, next.getId());
        return met ? "§a✔ Requirements Met" : "§c✘ Requirements Unmet";
    }

    private String getTopRank(int pos) {
        List<PlayerData> top = plugin.getRankManager().getCacheManager().getTopPlayers(
                plugin.getRankManager().getRankIds(), pos + 1);
        if (pos >= top.size()) return "§8—";
        PlayerData pd = top.get(pos);
        return "§e" + pd.playerName() + " §8[§7" + pd.rankId() + "§8]";
    }
}
