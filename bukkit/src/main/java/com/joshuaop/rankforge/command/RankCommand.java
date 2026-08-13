package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.gui.AnimatedRankTreeGUI;
import com.joshuaop.rankforge.permission.PermissionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/**
 * Main command dispatcher for /rank and the /ranks alias.
 *
 * Player subcommands are handled directly in this class.
 * Administrator subcommands are delegated to RankAdminCommand.
 *
 * Player subcommands:
 *   (none), up, progress, next, current, requirements,
 *   help, version, lang, history, xp (self-view)
 *
 * Admin subcommands (delegated):
 *   editor, create, delete/remove, set, reset, force,
 *   reload, debug, stats, security, sound, playerlist,
 *   xp set/add
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private final RankForge          plugin;
    private final RankAdminCommand   adminCmd;
    private final RankVersionCommand versionCmd;

    public RankCommand(RankForge plugin) {
        this.plugin     = plugin;
        RankEditorCommand editorCmd = new RankEditorCommand(plugin);
        RankReloadCommand reloadCmd = new RankReloadCommand(plugin);
        this.adminCmd   = new RankAdminCommand(plugin, editorCmd, reloadCmd);
        this.versionCmd = new RankVersionCommand(plugin);
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { requirePlayer(sender, PermissionRegistry.USE, this::openGui); return true; }

        switch (args[0].toLowerCase()) {
            // ── Player commands ─────────────────────────────────────────────
            case "up"           -> requirePlayer(sender, PermissionRegistry.USE_UP,           p -> plugin.getApi().rankUp(p));
            case "progress"     -> requirePlayer(sender, PermissionRegistry.USE_PROGRESS,     this::showProgress);
            case "next"         -> requirePlayer(sender, PermissionRegistry.USE_NEXT,         this::showNext);
            case "current"      -> requirePlayer(sender, PermissionRegistry.USE_CURRENT,      this::showCurrent);
            case "requirements" -> requirePlayer(sender, PermissionRegistry.USE_REQUIREMENTS, this::showRequirements);
            case "help"         -> sendHelp(sender);
            case "version"      -> {
                if (sender instanceof Player p && !p.hasPermission(PermissionRegistry.USE_VERSION)) {
                    plugin.getLangManager().send(p, "no_permission");
                } else {
                    versionCmd.handle(sender);
                }
            }
            case "lang"         -> requirePlayer(sender, PermissionRegistry.USE_LANG,         p -> doLang(p, args));
            case "history"      -> requirePlayer(sender, PermissionRegistry.USE_HISTORY,      this::showHistory);
            case "xp"           -> doXp(sender, args);
            // ── Admin commands (delegated) ──────────────────────────────────
            default             -> {
                boolean handled = adminCmd.handle(sender, args);
                if (!handled) sender.sendMessage("§cUnknown subcommand. Use §e/rank help§c.");
            }
        }
        return true;
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender s) {
        if (s instanceof Player p && !p.hasPermission(PermissionRegistry.USE_HELP)) {
            plugin.getLangManager().send(p, "no_permission");
            return;
        }
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

        boolean isAdmin = s.hasPermission(PermissionRegistry.ADMIN_STAR)
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
            s.sendMessage("  §c/rank bypassreq <player> <requirement> §8— §7Instantly complete a specific requirement for a player");
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

    private void showProgress(Player p) {
        var service = plugin.getApi().getProgressService();
        String bar  = service.getProgressBar(p);
        double pct  = service.getPercent(p);
        plugin.getLangManager().send(p, "progress_bar",
                Map.of("bar", bar, "percent", String.format("%.1f", pct)));

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
            p.sendMessage("§c[RankForge] History system not initialised.");
            return;
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
        // Admin branch: /rank xp set <player> <amount> or /rank xp add <player> <amount>
        if (args.length >= 4) {
            String sub = args[1].toLowerCase();
            if (sub.equals("set") || sub.equals("add")) {
                adminCmd.handleXpAdmin(s, args);
                return;
            }
        }
        // Player self-view
        requirePlayer(s, PermissionRegistry.USE_XP, this::showXpInfo);
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

    // ── Lang ──────────────────────────────────────────────────────────────────

    private void doLang(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cUsage: §e/rank lang <set|list|reset>"); return; }
        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (args.length < 3) { p.sendMessage("§cProvide a language code."); return; }
                if (!plugin.getLangManager().isValidLang(args[2])) {
                    p.sendMessage("§cUnknown language: §e" + args[2]
                            + "§c. Use §e/rank lang list§c.");
                    return;
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

    // ── Tab Completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        boolean isMasterAdmin = sender.hasPermission(PermissionRegistry.ADMIN_STAR) || sender.isOp();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "up", "progress", "next", "current", "requirements",
                    "help", "version", "lang", "history", "xp"));
            if (isMasterAdmin) {
                subs.addAll(List.of("editor", "set", "reset", "force", "reload",
                        "debug", "stats", "security", "sound", "playerlist",
                        "create", "delete", "remove", "bypassreq"));
            }
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "xp" -> {
                    // Admin sub-actions (set/add) are only shown to admins
                    if (isMasterAdmin) return filter(List.of("set", "add"), args[1]);
                }
                case "lang"  -> { return filter(List.of("set", "list", "reset"), args[1]); }
                default     -> {
                    if (isMasterAdmin) return adminCmd.tabComplete(sender, args);
                }
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "lang" -> {
                    if (args[1].equalsIgnoreCase("set")) {
                        return filter(new ArrayList<>(plugin.getLangManager().getAvailableLangs()), args[2]);
                    }
                }
                default -> {
                    if (isMasterAdmin) return adminCmd.tabComplete(sender, args);
                }
            }
        }

        if (args.length > 3 && isMasterAdmin) {
            return adminCmd.tabComplete(sender, args);
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
        PlayerData data = plugin.getRankManager().getRepository()
                .loadOrCreate(p.getUniqueId(), p.getName());
        return data.rankId();
    }

    private void requirePlayer(CommandSender s, String permission, Consumer<Player> action) {
        if (!(s instanceof Player p)) {
            s.sendMessage("§cThis command can only be run by a player.");
            return;
        }
        if (!p.hasPermission(permission)) {
            plugin.getLangManager().send(p, "no_permission");
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
