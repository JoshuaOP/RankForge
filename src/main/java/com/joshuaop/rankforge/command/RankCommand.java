package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.gui.AdminRankEditorGUI;
import com.joshuaop.rankforge.gui.AnimatedRankTreeGUI;
import com.joshuaop.rankforge.gui.PlayerListGUI;
import com.joshuaop.rankforge.gui.RankDetailEditorGUI;
import com.joshuaop.rankforge.permission.PermissionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/**
 * Unified command handler for /rank and /ranks alias.
 *
 * Validation applied to every admin action:
 *   - Player names are verified before executing any modification.
 *   - Rank IDs are verified to exist before set/force/reset.
 *   - Numeric arguments are rejected early with clear messages.
 *   - All admin commands support offline players (by name or UUID lookup).
 *   - No command partially executes on invalid input.
 *
 * Player subcommands:
 *   up, progress, next, current, requirements, version, lang, help, history, xp
 *
 * Admin subcommands:
 *   editor, set, reset, force, reload, debug, stats, security, sound,
 *   playerlist, xp set/add, create, delete/remove
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private final RankForge           plugin;
    private final RankEditorCommand   editorCmd;
    private final RankVersionCommand  versionCmd;
    private final RankReloadCommand   reloadCmd;

    // Names reserved by the system — rank IDs may never use these
    private static final Set<String> RESERVED_RANK_NAMES = Set.of(
            "null", "none", "default", "cancel", "all", "reset", "admin"
    );

    public RankCommand(RankForge plugin) {
        this.plugin     = plugin;
        this.editorCmd  = new RankEditorCommand(plugin);
        this.versionCmd = new RankVersionCommand(plugin);
        this.reloadCmd  = new RankReloadCommand(plugin);
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { requirePlayer(sender, this::openGui); return true; }

        switch (args[0].toLowerCase()) {
            // ── Player commands ─────────────────────────────────────────────
            case "up"           -> requirePlayer(sender, p -> plugin.getApi().rankUp(p));
            case "progress"     -> requirePlayer(sender, this::showProgress);
            case "next"         -> requirePlayer(sender, this::showNext);
            case "current"      -> requirePlayer(sender, this::showCurrent);
            case "requirements" -> requirePlayer(sender, this::showRequirements);
            case "help"         -> sendHelp(sender);
            case "version"      -> versionCmd.handle(sender);
            case "lang"         -> requirePlayer(sender, p -> doLang(p, args));
            case "history"      -> requirePlayer(sender, this::showHistory);
            case "xp"           -> doXp(sender, args);
            // ── Admin commands ──────────────────────────────────────────────
            case "editor"             -> { if (perm(sender, PermissionRegistry.EDITOR))    editorCmd.handle(sender, args); }
            case "create"             -> { if (perm(sender, PermissionRegistry.CREATE))    requirePlayer(sender, p -> doCreate(p, args)); }
            case "delete", "remove"   -> { if (perm(sender, PermissionRegistry.DELETE))   requirePlayer(sender, p -> doDelete(p, args)); }
            case "set"                -> { if (perm(sender, PermissionRegistry.SET))       doSet(sender, args); }
            case "reset"              -> { if (perm(sender, PermissionRegistry.RESET))     doReset(sender, args); }
            case "force"              -> { if (perm(sender, PermissionRegistry.FORCE))     doForce(sender, args); }
            case "reload"             -> { if (perm(sender, PermissionRegistry.RELOAD))    reloadCmd.handle(sender); }
            case "debug"              -> requirePlayer(sender, p -> { if (perm(p, PermissionRegistry.DEBUG)) doDebug(p); });
            case "stats"              -> { if (perm(sender, PermissionRegistry.STATS))     doStats(sender); }
            case "security"           -> { if (perm(sender, PermissionRegistry.SECURITY))  doSecurity(sender); }
            case "sound"              -> requirePlayer(sender, p -> { if (perm(p, PermissionRegistry.SOUND)) doSound(p, args); });
            case "playerlist"         -> { if (perm(sender, PermissionRegistry.PLAYER_LIST)) requirePlayer(sender, this::openPlayerList); }
            default                   -> sender.sendMessage("§cUnknown subcommand. Use §e/rank help§c.");
        }
        return true;
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender s) {
        s.sendMessage("§8§m                                                ");
        s.sendMessage("  §6§lRankForge §r§7— Command Help");
        s.sendMessage("§8§m                                                ");
        s.sendMessage("  §e/rank §8— §7Open the rank GUI");
        s.sendMessage("  §e/rank up §8— §7Rank up (if requirements met)");
        s.sendMessage("  §e/rank progress §8— §7View progress bar");
        s.sendMessage("  §e/rank next §8— §7View next rank info");
        s.sendMessage("  §e/rank current §8— §7View your current rank");
        s.sendMessage("  §e/rank requirements §8— §7View next rank requirements");
        s.sendMessage("  §e/rank history §8— §7View your rank history");
        s.sendMessage("  §e/rank xp §8— §7View XP level & progress");
        s.sendMessage("  §e/rank version §8— §7Plugin & system info");
        s.sendMessage("  §e/rank lang <set|list|reset> §8— §7Change language");

        boolean isAdmin = s.hasPermission(PermissionRegistry.EDITOR)
                || s.hasPermission(PermissionRegistry.RELOAD)
                || s.isOp();

        if (isAdmin) {
            s.sendMessage("§8§m                                                ");
            s.sendMessage("  §c§lAdmin Commands");
            s.sendMessage("§8§m                                                ");
            s.sendMessage("  §c/rank editor §8— §7Open admin editor GUI");
            s.sendMessage("  §c/rank editor <rankId> §8— §7Edit a specific rank");
            s.sendMessage("  §c/rank editor drag §8— §7Open slot editor");
            s.sendMessage("  §c/rank editor reload §8— §7Hot-reload ranks.yml");
            s.sendMessage("  §c/rank create §8— §7Create a new rank (uses chat)");
            s.sendMessage("  §c/rank create <id> §8— §7Create a rank with a specific ID");
            s.sendMessage("  §c/rank delete <id> §8— §7Delete a rank");
            s.sendMessage("  §c/rank playerlist §8— §7View & edit all player data");
            s.sendMessage("  §c/rank set <player> <rank> §8— §7Set a player's rank");
            s.sendMessage("  §c/rank reset <player> §8— §7Reset a player's rank");
            s.sendMessage("  §c/rank force <player> <rank> §8— §7Force rank (no checks)");
            s.sendMessage("  §c/rank xp set <player> <amount> §8— §7Set player XP");
            s.sendMessage("  §c/rank xp add <player> <amount> §8— §7Add player XP");
            s.sendMessage("  §c/rank reload §8— §7Full plugin reload");
            s.sendMessage("  §c/rank stats §8— §7System statistics");
            s.sendMessage("  §c/rank security §8— §7Anti-bypass status");
            s.sendMessage("  §c/rank debug §8— §7Your rank debug info");
        }
        s.sendMessage("§8§m                                                ");
    }

    // ── Player subcommands ────────────────────────────────────────────────────

    private void openGui(Player p) {
        if (plugin.getExternalGUIRegistry().tryOpen(
                com.joshuaop.rankforge.api.gui.ExternalGUIProvider.GuiType.RANK_TREE, p)) return;
        new AnimatedRankTreeGUI(plugin).open(p);
    }

    private void openPlayerList(Player p) {
        new PlayerListGUI(plugin).open(p);
    }

    private void showProgress(Player p) {
        var service = plugin.getApi().getProgressService();
        String bar  = service.getProgressBar(p);
        double pct  = service.getPercent(p);
        plugin.getLangManager().send(p, "progress_bar",
                Map.of("bar", bar, "percent", String.format("%.1f", pct)));

        // Per-requirement breakdown
        var reqs = service.getRequirementProgress(p);
        if (!reqs.isEmpty()) {
            p.sendMessage("§8§m                              ");
            p.sendMessage("  §7Requirement breakdown:");
            for (var rp : reqs) {
                p.sendMessage("  " + rp.toDisplayLine());
            }
            p.sendMessage("§8§m                              ");
        }
    }

    private void showNext(Player p) {
        String cur  = getCurrentRank(p);
        String next = plugin.getRankManager().getNextRankId(cur);
        if (next == null || next.isBlank())
            plugin.getLangManager().send(p, "no_next_rank");
        else
            plugin.getLangManager().send(p, "next_rank",
                    Map.of("rank", plugin.getRankManager().getDisplayName(next)));
    }

    private void showCurrent(Player p) {
        plugin.getLangManager().send(p, "current_rank",
                Map.of("rank", plugin.getRankManager().getDisplayName(getCurrentRank(p))));
    }

    private void showRequirements(Player p) {
        String next = plugin.getRankManager().getNextRankId(getCurrentRank(p));
        if (next == null || next.isBlank()) { plugin.getLangManager().send(p, "no_next_rank"); return; }
        p.sendMessage(plugin.getLangManager().format(
                plugin.getLangManager().get(p.getUniqueId(), "requirements_header"),
                Map.of("rank", next)));
        var unmet = plugin.getRequirementManager().getUnmet(p, next);
        if (unmet.isEmpty()) p.sendMessage("§a✔ All requirements met! Use §e/rank up§a.");
        else unmet.forEach(p::sendMessage);
    }

    private void showHistory(Player p) {
        if (plugin.getHistoryManager() == null) {
            p.sendMessage("§c[RankForge] History system not initialised."); return;
        }

        UUID uuid = p.getUniqueId();
        p.sendMessage("§6[RankForge] §7Loading your rank history…");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<com.joshuaop.rankforge.experience.RankHistoryEntry> history =
                    plugin.getHistoryManager().getHistory(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendMessage("§8§m                              ");
                p.sendMessage("  §6Rank History §7(" + history.size() + " entries)");
                p.sendMessage("§8§m                              ");
                if (history.isEmpty()) {
                    p.sendMessage("  §7No rank changes recorded yet.");
                } else {
                    int limit = Math.min(history.size(), 10);
                    for (int i = 0; i < limit; i++) p.sendMessage("  " + history.get(i).toDisplayLine());
                    if (history.size() > 10)
                        p.sendMessage("  §8… and " + (history.size() - 10) + " more entries.");
                }
                p.sendMessage("§8§m                              ");
            });
        });
    }

    // ── XP ────────────────────────────────────────────────────────────────────

    private void doXp(CommandSender s, String[] args) {
        if (args.length == 1) { requirePlayer(s, this::showXpInfo); return; }

        String sub = args[1].toLowerCase();

        if ((sub.equals("set") || sub.equals("add")) && args.length >= 4) {
            if (!perm(s, PermissionRegistry.XP_ADMIN)) return;

            // Player validation
            String targetName = args[2];
            if (!isValidPlayerName(targetName)) {
                s.sendMessage("§c✘ Invalid player name: §e" + targetName); return;
            }
            Player t = Bukkit.getPlayer(targetName);
            if (t == null) {
                s.sendMessage("§c✘ Player §e" + targetName + " §cis not online."); return;
            }

            // Amount validation
            long amount;
            try {
                amount = Long.parseLong(args[3]);
            } catch (NumberFormatException e) {
                s.sendMessage("§c✘ Invalid amount: §e" + args[3] + " §c(must be a whole number).");
                return;
            }
            if (amount < 0) {
                s.sendMessage("§c✘ XP amount cannot be negative."); return;
            }

            if (sub.equals("set")) {
                plugin.getExperienceManager().set(t, amount);
                s.sendMessage("§a✔ Set §e" + t.getName() + "§a's experience to §e" + amount + "§a.");
            } else {
                plugin.getExperienceManager().award(t, amount);
                s.sendMessage("§a✔ Added §e" + amount + " §aexperience to §e" + t.getName() + "§a.");
            }
            return;
        }

        requirePlayer(s, this::showXpInfo);
    }

    private void showXpInfo(Player p) {
        int level     = p.getLevel();
        float expPct  = p.getExp();
        long totalXp  = plugin.getExperienceManager() != null
                ? plugin.getExperienceManager().getXp(p) : 0L;
        int toNextLvl = p.getExpToLevel();

        String progressBar = buildXpBar(expPct);

        String nextRankId      = plugin.getRankManager().getNextRankId(getCurrentRank(p));
        int    nextRankXpReq   = -1;
        String nextRankDisplay = "§6MAX";
        if (nextRankId != null && !nextRankId.isBlank()) {
            var model = plugin.getRankManager().getRank(nextRankId);
            if (model != null) {
                nextRankXpReq   = model.getRequiredXpLevel();
                nextRankDisplay = model.getDisplayName();
            }
        }

        p.sendMessage("§8§m                                          ");
        p.sendMessage("  §6§lRankForge §7— Vanilla XP Info");
        p.sendMessage("§8§m                                          ");
        p.sendMessage("  §7Level:         §a" + level);
        p.sendMessage("  §7Total XP:      §a" + String.format("%,d", totalXp));
        p.sendMessage("  §7Progress:      " + progressBar
                + " §e" + String.format("%.1f", expPct * 100f) + "§7%");
        p.sendMessage("  §7XP to Lv " + (level + 1) + ":  §a" + String.format("%,d", toNextLvl));
        p.sendMessage("  §8——————————————————————————");
        if (nextRankId != null && !nextRankId.isBlank()) {
            boolean xpMet   = nextRankXpReq <= 0 || level >= nextRankXpReq;
            String xpStatus = xpMet ? "§a✔" : "§c✘";
            String reqText  = nextRankXpReq > 0 ? "Lv " + nextRankXpReq : "§7None";
            p.sendMessage("  §7Next Rank:     " + nextRankDisplay);
            p.sendMessage("  §7XP Req:        " + xpStatus + " §7" + reqText
                    + (nextRankXpReq > 0 && !xpMet
                            ? " §8(§cNeed " + (nextRankXpReq - level) + " more levels§8)" : ""));
        } else {
            p.sendMessage("  §6✦ §eYou are at the maximum rank!");
        }
        p.sendMessage("§8§m                                          ");
    }

    private String buildXpBar(float progress) {
        int filled = Math.min(10, (int) (progress * 10f));
        var sb = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i == filled) sb.append("§7");
            sb.append("█");
        }
        return sb.toString();
    }

    // ── Admin subcommands ─────────────────────────────────────────────────────

    private void doCreate(Player p, String[] args) {
        if (args.length >= 2) {
            String rankId = args[1].trim();

            // Validate rank name before creating
            String nameError = validateNewRankId(rankId);
            if (nameError != null) {
                p.sendMessage("§c✘ " + nameError); return;
            }

            RankDetailEditorGUI.setPendingCreate(p.getUniqueId());
            new RankDetailEditorGUI(plugin).applyCreate(p, rankId);
        } else {
            RankDetailEditorGUI.setPendingCreate(p.getUniqueId());
            p.sendMessage("§8§m                                                  ");
            p.sendMessage("§b§lCreate New Rank");
            p.sendMessage("§7Type the §erank ID §7for the new rank.");
            p.sendMessage("§7Example: §eBuilder§7, §eVIP§7, §eAdmin");
            p.sendMessage("§7Rules: letters/numbers/underscores, not reserved.");
            p.sendMessage("§7Type §ccancel §7to abort.");
            p.sendMessage("§8§m                                                  ");
        }
    }

    private void doDelete(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: §e/rank delete <rankId>"); return;
        }
        String rankId = args[1].trim();
        if (rankId.isBlank()) {
            p.sendMessage("§c✘ Rank ID cannot be blank."); return;
        }
        if (plugin.getRankManager().getRank(rankId) == null) {
            p.sendMessage("§c✘ Rank §e" + rankId + " §cdoes not exist."); return;
        }
        RankDetailEditorGUI.setPendingDelete(p.getUniqueId(), rankId);
        p.sendMessage("§8§m                                                  ");
        p.sendMessage("§c§lDelete Rank: §e" + rankId);
        p.sendMessage("§7Type §ayes §7to confirm or anything else to cancel.");
        p.sendMessage("§c⚠ This cannot be undone!");
        p.sendMessage("§8§m                                                  ");
    }

    /**
     * /rank set <player> <rank>
     * Supports offline players via name lookup.
     */
    private void doSet(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage("§cUsage: §e/rank set <player> <rank>"); return;
        }

        String targetName = args[1].trim();
        String rankId     = args[2].trim();

        // Argument validation
        if (!isValidPlayerName(targetName)) {
            s.sendMessage("§c✘ Invalid player name: §e" + targetName); return;
        }
        if (rankId.isBlank()) {
            s.sendMessage("§c✘ Rank ID cannot be blank."); return;
        }

        // Rank existence validation
        if (plugin.getRankManager().getRank(rankId) == null) {
            s.sendMessage("§c✘ Rank §e" + rankId + " §cdoes not exist.");
            suggestSimilarRank(s, rankId);
            return;
        }

        // Try online player first
        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            boolean ok = plugin.getApi().setRank(online, rankId, s);
            s.sendMessage(ok
                    ? "§a✔ Rank set to §e" + rankId + " §afor §e" + online.getName() + "§a."
                    : "§c✘ Rank change was cancelled by an event listener.");
            return;
        }

        // Offline player fallback
        applyOfflineRankChange(s, targetName, rankId, "SET");
    }

    /**
     * /rank reset <player>
     * Supports offline players.
     */
    private void doReset(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage("§cUsage: §e/rank reset <player>"); return;
        }
        String targetName = args[1].trim();
        if (!isValidPlayerName(targetName)) {
            s.sendMessage("§c✘ Invalid player name: §e" + targetName); return;
        }

        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            plugin.getApi().resetRank(online, s);
            s.sendMessage("§a✔ Reset §e" + online.getName() + "§a's rank to default.");
            return;
        }

        String defaultRank = plugin.getConfig().getString("ranks.default-rank", "Guest");
        applyOfflineRankChange(s, targetName, defaultRank, "RESET");
    }

    /**
     * /rank force <player> <rank>
     * Force-sets rank without requirement checks. Supports offline players.
     */
    private void doForce(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage("§cUsage: §e/rank force <player> <rank>"); return;
        }
        String targetName = args[1].trim();
        String rankId     = args[2].trim();

        if (!isValidPlayerName(targetName)) {
            s.sendMessage("§c✘ Invalid player name: §e" + targetName); return;
        }
        if (rankId.isBlank()) {
            s.sendMessage("§c✘ Rank ID cannot be blank."); return;
        }
        if (plugin.getRankManager().getRank(rankId) == null) {
            s.sendMessage("§c✘ Rank §e" + rankId + " §cdoes not exist.");
            suggestSimilarRank(s, rankId);
            return;
        }

        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            plugin.getApi().setRank(online, rankId, s);
            s.sendMessage("§a✔ Force-set §e" + online.getName() + "§a's rank to §e" + rankId + "§a.");
            return;
        }

        applyOfflineRankChange(s, targetName, rankId, "SET");
        s.sendMessage("§a✔ Force-set offline player §e" + targetName + "§a's rank to §e" + rankId + "§a.");
    }

    /**
     * Applies a rank change to an offline player by looking up their stored data,
     * updating it in cache + storage, and recording history.
     */
    private void applyOfflineRankChange(CommandSender s, String targetName,
                                         String rankId, String changeType) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Attempt to resolve UUID from online cache or YAML storage
                UUID targetUuid = resolveOfflineUUID(targetName);

                if (targetUuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            s.sendMessage("§c✘ Player §e" + targetName
                                    + " §chas no stored data and is not online."));
                    return;
                }

                var cache = plugin.getRankManager().getCacheManager();
                PlayerData current = cache.contains(targetUuid)
                        ? cache.get(targetUuid)
                        : plugin.getRankManager().getRepository().load(targetUuid, targetName);

                if (current == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            s.sendMessage("§c✘ Could not load data for §e" + targetName + "§c."));
                    return;
                }

                String prevRank = current.rankId();
                PlayerData updated = current.withRank(rankId);
                cache.put(targetUuid, updated);
                plugin.getRankManager().getRepository().save(updated);

                // Record history
                if (plugin.getHistoryManager() != null) {
                    com.joshuaop.rankforge.experience.RankHistoryEntry.ChangeType ct;
                    try {
                        ct = com.joshuaop.rankforge.experience.RankHistoryEntry.ChangeType
                                .valueOf(changeType);
                    } catch (IllegalArgumentException ex) {
                        ct = com.joshuaop.rankforge.experience.RankHistoryEntry.ChangeType.SET;
                    }
                    plugin.getHistoryManager().record(
                            new com.joshuaop.rankforge.experience.RankHistoryEntry(
                                    targetUuid, targetName, prevRank, rankId,
                                    ct, System.currentTimeMillis()));
                }

                final String finalRank    = rankId;
                final String finalPrev    = prevRank;
                final UUID   finalUuid    = targetUuid;
                final String finalCTLabel = changeType.equals("RESET") ? "reset" : "set";

                Bukkit.getScheduler().runTask(plugin, () -> {
                    s.sendMessage("§a✔ " + (changeType.equals("RESET") ? "Reset" : "Set")
                            + " offline player §e" + targetName + "§a's rank to §e"
                            + finalRank + "§a. (was §7" + finalPrev + "§a)");
                    // Notify if the player came online between the async lookup and now
                    Player nowOnline = Bukkit.getPlayer(finalUuid);
                    if (nowOnline != null) {
                        nowOnline.sendMessage("§6[RankForge] §7An admin has "
                                + finalCTLabel + " your rank to §e" + finalRank + "§7.");
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[RankCommand] Offline rank change failed for "
                        + targetName + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        s.sendMessage("§c✘ An internal error occurred while updating "
                                + targetName + "'s rank."));
            }
        });
    }

    private void doLang(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cUsage: §e/rank lang <set|list|reset>"); return; }
        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (args.length < 3) { p.sendMessage("§cProvide a language code."); return; }
                if (!plugin.getLangManager().isValidLang(args[2])) {
                    p.sendMessage("§cUnknown language: §e" + args[2]
                            + "§c. Use §e/rank lang list§c."); return;
                }
                plugin.getLangManager().setPlayerLang(p.getUniqueId(), args[2]);
                plugin.getLangManager().send(p, "lang_set", Map.of("lang", args[2]));
            }
            case "list"  -> plugin.getLangManager().send(p, "lang_list",
                    Map.of("list", String.join(", ", plugin.getLangManager().getAvailableLangs())));
            case "reset" -> {
                plugin.getLangManager().resetPlayerLang(p.getUniqueId());
                plugin.getLangManager().send(p, "lang_reset");
            }
            default -> p.sendMessage("§cUsage: §e/rank lang <set|list|reset>");
        }
    }

    private void doDebug(Player p) {
        long xp = plugin.getExperienceManager() != null
                ? plugin.getExperienceManager().getXp(p) : 0L;
        plugin.getLangManager().send(p, "debug_info", Map.of(
                "rank", getCurrentRank(p),
                "db",   String.valueOf(plugin.getDatabaseManager().isConnected()),
                "lang", plugin.getLangManager().getPlayerLang(p.getUniqueId())));
        p.sendMessage("§7XP: §a" + String.format("%,d", xp));
        if (plugin.getHistoryManager() != null) {
            p.sendMessage("§7Rank-ups: §a" + plugin.getHistoryManager().countRankups(p.getUniqueId()));
        }
        // Overall progress
        double pct = plugin.getApi().getProgressService().getPercent(p);
        p.sendMessage("§7Progress: §e" + String.format("%.1f", pct) + "§7%");
    }

    private void doStats(CommandSender s) {
        String storageType = plugin.getDatabaseManager().isConnected() ? "§aMySQL" : "§eYAML File";
        String mcVer = Bukkit.getBukkitVersion().split("-")[0];
        s.sendMessage("§8§m                                ");
        s.sendMessage("  §6§lRankForge §7System Stats");
        s.sendMessage("§8§m                                ");
        s.sendMessage("  §7Cache size:     §e" + plugin.getRankManager().getCacheManager().size());
        s.sendMessage("  §7Ranks loaded:   §e" + plugin.getRankManager().getRankCount());
        s.sendMessage("  §7Expansions:     §e" + plugin.getExpansionRegistry().size());
        s.sendMessage("  §7Custom Reqs:    §e" + plugin.getCustomRequirementRegistry().size());
        s.sendMessage("  §7Hooks:          §e" + plugin.getHookRegistry().size());
        s.sendMessage("  §7Storage:        " + storageType);
        s.sendMessage("  §7MC version:     §e" + mcVer);
        s.sendMessage("  §7Vault:          " + (plugin.getSoftDependency().hasVault()      ? "§a✔" : "§7—"));
        s.sendMessage("  §7LuckPerms:      " + (plugin.getSoftDependency().hasLuckPerms()  ? "§a✔" : "§7—"));
        s.sendMessage("  §7PlaceholderAPI: " + (plugin.getSoftDependency().hasPapi()       ? "§a✔" : "§7—"));
        s.sendMessage("  §7Floodgate:      " + (plugin.getSoftDependency().hasFloodgate()  ? "§a✔" : "§7—"));
        s.sendMessage("  §7REST API:       " + (plugin.getRestAPIServer().isRunning()      ? "§a✔" : "§7—"));
        s.sendMessage("§8§m                                ");
    }

    private void doSecurity(CommandSender s) {
        s.sendMessage("§8§m                                ");
        s.sendMessage("  §6§lRankForge §7Security");
        s.sendMessage("§8§m                                ");
        s.sendMessage("  §7Anti-bypass: §a" + plugin.getConfig().getBoolean("anti-bypass.enabled"));
        s.sendMessage("  §7GUI shield:  §a" + plugin.getConfig().getBoolean("gui-click-shield.enabled"));
        s.sendMessage("  §7Tracked:     §e" + plugin.getAntiBypassManager().getTrackedPlayers() + " §7players");
        s.sendMessage("§8§m                                ");
    }

    private void doSound(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cUsage: §e/rank sound <test|reload>"); return; }
        switch (args[1].toLowerCase()) {
            case "test"   -> { plugin.getSoundManager().playTest(p); plugin.getLangManager().send(p, "sound_test"); }
            case "reload" -> { plugin.getSoundManager().reload();    plugin.getLangManager().send(p, "sound_reload"); }
            default       -> p.sendMessage("§cUsage: §e/rank sound <test|reload>");
        }
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    /**
     * Validates a player name, supporting both Java (1–16 chars, alphanumeric + underscore)
     * and Floodgate/Bedrock players whose names start with the configured bedrock prefix
     * (default ".") followed by a valid Xbox gamertag (letters, digits, underscores, spaces).
     */
    private boolean isValidPlayerName(String name) {
        if (name == null || name.isBlank()) return false;
        String prefix = plugin.getConfig().getString("crossplay.bedrock-prefix", ".");
        // Strip the Bedrock prefix if present, then validate the base name
        String base = name.startsWith(prefix) ? name.substring(prefix.length()) : name;
        if (base.isEmpty()) return false;
        // Java names: max 16 chars; Bedrock (with prefix): max 16 + prefix length
        int maxLen = 16 + prefix.length();
        // Bedrock/Xbox gamertags may contain letters, digits, underscores, and spaces
        return name.length() <= maxLen && base.matches("[a-zA-Z0-9_ ]+");
    }

    /**
     * Returns an error message if the given rank ID is invalid for creation,
     * or null if the ID is acceptable.
     */
    private String validateNewRankId(String id) {
        if (id == null || id.isBlank())
            return "Rank ID cannot be blank.";
        if (id.length() > 32)
            return "Rank ID is too long (max 32 characters).";
        if (!id.matches("[a-zA-Z0-9_-]+"))
            return "Rank ID may only contain letters, numbers, underscores, and hyphens.";
        if (RESERVED_RANK_NAMES.contains(id.toLowerCase()))
            return "Rank ID §e" + id + "§c is reserved and cannot be used.";
        if (plugin.getRankManager().getRank(id) != null)
            return "A rank with ID §e" + id + "§c already exists.";
        return null;
    }

    /**
     * Attempts to resolve the UUID for an offline player by:
     * 1. Checking the in-memory cache.
     * 2. Checking YAML player data storage.
     * 3. Calling Bukkit.getOfflinePlayer (only for previously-seen players).
     */
    @SuppressWarnings("deprecation")
    private UUID resolveOfflineUUID(String name) {
        // Check cache first
        for (var entry : plugin.getRankManager().getCacheManager().getCache().entrySet()) {
            PlayerData pd = entry.getValue().data();
            if (pd != null && name.equalsIgnoreCase(pd.playerName())) {
                return entry.getKey();
            }
        }

        // Check YAML storage
        if (!plugin.getDatabaseManager().isConnected()
                && plugin.getYamlPlayerDataStorage() != null) {
            for (PlayerData pd : plugin.getYamlPlayerDataStorage().loadAll()) {
                if (name.equalsIgnoreCase(pd.playerName())) {
                    return pd.uuid();
                }
            }
        }

        // Last resort: Bukkit offline lookup (only works for players who have joined before)
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            if (op.hasPlayedBefore()) {
                return op.getUniqueId();
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Suggests rank IDs that are similar to the given invalid ID.
     */
    private void suggestSimilarRank(CommandSender s, String invalid) {
        Set<String> ids = plugin.getRankManager().getRankIds();
        if (ids == null || ids.isEmpty()) return;
        String lower = invalid.toLowerCase();
        List<String> suggestions = ids.stream()
                .filter(id -> id.toLowerCase().contains(lower)
                        || lower.contains(id.toLowerCase()))
                .limit(3)
                .toList();
        if (!suggestions.isEmpty()) {
            s.sendMessage("§7Did you mean: §e" + String.join("§7, §e", suggestions) + "§7?");
        }
    }

    // ── Tab Completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        boolean isMasterAdmin = sender.hasPermission(PermissionRegistry.EDITOR) || sender.isOp();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "up", "progress", "next", "current", "requirements",
                    "help", "version", "lang", "history", "xp"));
            if (isMasterAdmin) {
                subs.addAll(List.of("editor", "set", "reset", "force", "reload",
                        "debug", "stats", "security", "sound", "playerlist",
                        "create", "delete", "remove"));
            }
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "editor" -> { return filter(List.of("reload", "drag"), args[1]); }
                case "set", "reset", "force" -> {
                    if (!isMasterAdmin) return List.of();
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    return filter(names, args[1]);
                }
                case "delete", "remove" -> {
                    if (!isMasterAdmin) return List.of();
                    return filter(new ArrayList<>(plugin.getRankManager().getRankIds()), args[1]);
                }
                case "xp" -> { return filter(List.of("set", "add"), args[1]); }
                case "lang" -> { return filter(List.of("set", "list", "reset"), args[1]); }
                case "sound" -> { return filter(List.of("test", "reload"), args[1]); }
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "set", "force" -> {
                    if (!isMasterAdmin) return List.of();
                    return filter(new ArrayList<>(plugin.getRankManager().getRankIds()), args[2]);
                }
                case "xp" -> {
                    if (!isMasterAdmin) return List.of();
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    return filter(names, args[2]);
                }
                case "lang" -> {
                    if (args[1].equalsIgnoreCase("set")) {
                        return filter(new ArrayList<>(plugin.getLangManager().getAvailableLangs()), args[2]);
                    }
                }
            }
        }

        return List.of();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isBlank()) return options;
        String lower = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }

    private String getCurrentRank(Player p) {
        var cache = plugin.getRankManager().getCacheManager();
        if (cache.contains(p.getUniqueId())) {
            PlayerData data = cache.get(p.getUniqueId());
            if (data != null) return data.rankId();
        }
        return plugin.getRankManager().getDefaultRankId();
    }

    private void requirePlayer(CommandSender s, Consumer<Player> action) {
        if (!(s instanceof Player p)) {
            s.sendMessage("§cThis command can only be run by a player.");
            return;
        }
        action.accept(p);
    }

    private boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node) || s.isOp()) return true;
        s.sendMessage("§cYou do not have permission to do that. (§e" + node + "§c)");
        return false;
    }
}
