package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.experience.LeaderboardManager;
import com.joshuaop.rankforge.experience.RankHistoryEntry;
import com.joshuaop.rankforge.experience.RankStatistics;
import com.joshuaop.rankforge.gui.AnimatedRankTreeGUI;
import com.joshuaop.rankforge.gui.PlayerListGUI;
import com.joshuaop.rankforge.permission.PermissionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/**
 * Unified command handler for /rank (and /ranks alias).
 *
 * <h3>Player subcommands:</h3>
 * up, progress, next, current, requirements, version, lang, help,
 * daily, history, challenges, quests, xp, leaderboard
 *
 * <h3>Admin subcommands:</h3>
 * editor, set, reset, force, reload, debug, stats, security, sound,
 * playerlist, xp set/add
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private final RankForge           plugin;
    private final RankEditorCommand   editorCmd;
    private final RankVersionCommand  versionCmd;
    private final RankReloadCommand   reloadCmd;

    public RankCommand(RankForge plugin) {
        this.plugin     = plugin;
        this.editorCmd  = new RankEditorCommand(plugin);
        this.versionCmd = new RankVersionCommand(plugin);
        this.reloadCmd  = new RankReloadCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { requirePlayer(sender, this::openGui); return true; }

        switch (args[0].toLowerCase()) {
            // ── Player commands ───────────────────────────────────────────────
            case "up"           -> requirePlayer(sender, p -> plugin.getApi().rankUp(p));
            case "progress"     -> requirePlayer(sender, this::showProgress);
            case "next"         -> requirePlayer(sender, this::showNext);
            case "current"      -> requirePlayer(sender, this::showCurrent);
            case "requirements" -> requirePlayer(sender, this::showRequirements);
            case "help"         -> sendHelp(sender);
            case "version"      -> versionCmd.handle(sender);
            case "lang"         -> requirePlayer(sender, p -> doLang(p, args));
            case "daily"        -> requirePlayer(sender, this::doDaily);
            case "history"      -> requirePlayer(sender, this::showHistory);
            case "challenges"   -> requirePlayer(sender, this::showChallenges);
            case "quests"       -> requirePlayer(sender, this::showQuests);
            case "xp"           -> doXp(sender, args);
            case "leaderboard", "top" -> showLeaderboard(sender, args);
            // ── Admin commands ────────────────────────────────────────────────
            case "editor"     -> editorCmd.handle(sender, args);
            case "set"        -> { if (perm(sender, PermissionRegistry.SET))       doSet(sender, args); }
            case "reset"      -> { if (perm(sender, PermissionRegistry.RESET))     doReset(sender, args); }
            case "force"      -> { if (perm(sender, PermissionRegistry.FORCE))     doForce(sender, args); }
            case "reload"     -> reloadCmd.handle(sender);
            case "debug"      -> requirePlayer(sender, p -> { if (perm(p, PermissionRegistry.DEBUG)) doDebug(p); });
            case "stats"      -> { if (perm(sender, PermissionRegistry.STATS))     doStats(sender); }
            case "security"   -> { if (perm(sender, PermissionRegistry.SECURITY))  doSecurity(sender); }
            case "sound"      -> requirePlayer(sender, p -> { if (perm(p, PermissionRegistry.SOUND)) doSound(p, args); });
            case "playerlist" -> { if (perm(sender, PermissionRegistry.PLAYER_LIST)) requirePlayer(sender, this::openPlayerList); }
            default           -> sender.sendMessage("§cUnknown subcommand. Use §e/rank help §cfor a list.");
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
        s.sendMessage("  §e/rank requirements §8— §7View rank requirements");
        s.sendMessage("  §e/rank leaderboard §8— §7View top-10 leaderboard");
        s.sendMessage("  §e/rank history §8— §7View your rank history");
        s.sendMessage("  §e/rank challenges §8— §7View available challenges");
        s.sendMessage("  §e/rank quests §8— §7View available quests");
        s.sendMessage("  §e/rank daily §8— §7Claim your daily reward");
        s.sendMessage("  §e/rank xp §8— §7View your XP");
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
            s.sendMessage("  §c/rank editor save §8— §7Save to ranks.yml");
            s.sendMessage("  §c/rank editor reload §8— §7Hot-reload ranks.yml");
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
        // Try external GUI provider first
        if (plugin.getExternalGUIRegistry().tryOpen(
                com.joshuaop.rankforge.api.gui.ExternalGUIProvider.GuiType.RANK_TREE, p)) return;
        new AnimatedRankTreeGUI(plugin).open(p);
    }

    private void openPlayerList(Player p) {
        new PlayerListGUI(plugin).open(p);
    }

    private void showProgress(Player p) {
        String bar = plugin.getApi().getProgressService().getProgressBar(p);
        double pct = plugin.getApi().getProgress(p);
        plugin.getLangManager().send(p, "progress_bar",
                Map.of("bar", bar, "percent", String.format("%.1f", pct)));
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
                plugin.getLangManager().get(p.getUniqueId(), "requirements_header"), Map.of("rank", next)));
        var unmet = plugin.getRequirementManager().getUnmet(p, next);
        if (unmet.isEmpty()) p.sendMessage("§a✔ All requirements met! Use §e/rank up§a.");
        else unmet.forEach(p::sendMessage);
    }

    // ── Daily Reward ──────────────────────────────────────────────────────────

    private void doDaily(Player p) {
        if (plugin.getDailyRewardManager() == null) {
            p.sendMessage("§c[RankForge] Daily rewards system is not initialised.");
            return;
        }
        plugin.getDailyRewardManager().claimReward(p);
    }

    // ── Rank History ──────────────────────────────────────────────────────────

    private void showHistory(Player p) {
        if (plugin.getHistoryManager() == null) {
            p.sendMessage("§c[RankForge] History system not initialised."); return;
        }
        var history = plugin.getHistoryManager().getHistory(p.getUniqueId());
        p.sendMessage("§8§m                              ");
        p.sendMessage("  §6Rank History §7(" + history.size() + " entries)");
        p.sendMessage("§8§m                              ");
        if (history.isEmpty()) {
            p.sendMessage("  §7No rank changes recorded yet.");
        } else {
            int limit = Math.min(history.size(), 10);
            for (int i = 0; i < limit; i++) p.sendMessage("  " + history.get(i).toDisplayLine());
            if (history.size() > 10) p.sendMessage("  §8… and " + (history.size() - 10) + " more entries.");
        }
        p.sendMessage("§8§m                              ");
    }

    // ── Challenges ────────────────────────────────────────────────────────────

    private void showChallenges(Player p) {
        if (plugin.getChallengeManager() == null) {
            p.sendMessage("§c[RankForge] Challenges system not initialised."); return;
        }
        var available = plugin.getChallengeManager().getAvailable(p);
        p.sendMessage("§8§m                              ");
        p.sendMessage("  §6§lChallenges §7(" + available.size() + " available)");
        p.sendMessage("§8§m                              ");
        if (available.isEmpty()) {
            p.sendMessage("  §7No challenges available right now.");
        } else {
            for (var c : available) {
                int progress   = plugin.getChallengeManager().getProgress(p, c.getId());
                boolean onCD   = plugin.getChallengeManager().isOnCooldown(p, c.getId());
                String status  = onCD ? "§c[Cooldown]" : "§a[Active]";
                p.sendMessage("  " + status + " §e" + c.getName()
                        + " §8— §7" + progress + "/" + c.getTargetCount()
                        + " §8— §7" + c.getDescription());
            }
        }
        p.sendMessage("§8§m                              ");
    }

    // ── Quests ────────────────────────────────────────────────────────────────

    private void showQuests(Player p) {
        if (plugin.getQuestManager() == null) {
            p.sendMessage("§c[RankForge] Quest system not initialised."); return;
        }
        var available = plugin.getQuestManager().getAvailable(p);
        p.sendMessage("§8§m                              ");
        p.sendMessage("  §6§lQuests §7(" + available.size() + " available)");
        p.sendMessage("§8§m                              ");
        if (available.isEmpty()) {
            p.sendMessage("  §7No quests available right now.");
        } else {
            for (var q : available) {
                int step  = plugin.getQuestManager().getCurrentStep(p, q.getId());
                boolean c = plugin.getQuestManager().isCompleted(p, q.getId());
                String status = c ? "§a[Complete]" : "§e[Step " + (step + 1) + "/" + q.getTotalSteps() + "]";
                p.sendMessage("  " + status + " §6" + q.getName() + " §8— §7" + q.getDescription());
            }
        }
        p.sendMessage("§8§m                              ");
    }

    // ── XP ────────────────────────────────────────────────────────────────────

    private void doXp(CommandSender s, String[] args) {
        // /rank xp           → show own XP (player only)
        // /rank xp set <p> <n>  → admin set
        // /rank xp add <p> <n>  → admin add
        if (args.length == 1) {
            requirePlayer(s, p -> {
                long xp = plugin.getExperienceManager().getXp(p);
                p.sendMessage("§6[RankForge] §7Your XP: §a" + String.format("%,d", xp));
            });
            return;
        }

        String sub = args[1].toLowerCase();
        if ((sub.equals("set") || sub.equals("add")) && args.length >= 4) {
            if (!perm(s, PermissionRegistry.XP_ADMIN)) return;
            Player t = Bukkit.getPlayer(args[2]);
            if (t == null) { s.sendMessage("§cPlayer not found or not online."); return; }
            long amount;
            try { amount = Long.parseLong(args[3]); }
            catch (NumberFormatException e) { s.sendMessage("§cInvalid amount: §e" + args[3]); return; }

            if (sub.equals("set")) {
                plugin.getExperienceManager().set(t, amount);
                s.sendMessage("§aSet §e" + t.getName() + "§a's XP to §e" + amount + "§a.");
            } else {
                plugin.getExperienceManager().award(t, amount);
                s.sendMessage("§aAdded §e" + amount + " §aXP to §e" + t.getName() + "§a.");
            }
        } else {
            s.sendMessage("§cUsage: /rank xp | /rank xp set <player> <amount> | /rank xp add <player> <amount>");
        }
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    private void showLeaderboard(CommandSender s, String[] args) {
        if (!s.hasPermission(PermissionRegistry.LEADERBOARD) && !s.isOp()) {
            s.sendMessage("§cNo permission: §e" + PermissionRegistry.LEADERBOARD); return;
        }
        if (plugin.getLeaderboardManager() == null) {
            s.sendMessage("§c[RankForge] Leaderboard system not initialised."); return;
        }

        // Optional mode arg: /rank leaderboard [xp|rank]
        LeaderboardManager.SortMode mode = LeaderboardManager.SortMode.RANK_POSITION;
        if (args.length >= 2 && args[1].equalsIgnoreCase("xp"))
            mode = LeaderboardManager.SortMode.EXPERIENCE;

        var top = plugin.getLeaderboardManager().getTop(10, mode);
        String modeLabel = mode == LeaderboardManager.SortMode.EXPERIENCE ? "XP" : "Rank";
        s.sendMessage("§8§m                              ");
        s.sendMessage("  §6§lLeaderboard §7(Top 10 by " + modeLabel + ")");
        s.sendMessage("§8§m                              ");
        if (top.isEmpty()) {
            s.sendMessage("  §7No player data available yet.");
        } else {
            for (var entry : top) s.sendMessage("  " + entry.toDisplayLine());
        }
        s.sendMessage("§8§m                              ");
    }

    // ── Admin subcommands ─────────────────────────────────────────────────────

    private void doSet(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage("§cUsage: /rank set <player> <rank>"); return; }
        Player t = Bukkit.getPlayer(args[1]);
        if (t == null) { s.sendMessage("§cPlayer not found or not online."); return; }
        boolean ok = plugin.getApi().setRank(t, args[2], s);
        s.sendMessage(ok ? "§aRank set to §e" + args[2] + "§a for §e" + t.getName() + "§a."
                : "§cRank ID not found or event was cancelled.");
    }

    private void doReset(CommandSender s, String[] args) {
        if (args.length < 2) { s.sendMessage("§cUsage: /rank reset <player>"); return; }
        Player t = Bukkit.getPlayer(args[1]);
        if (t == null) { s.sendMessage("§cPlayer not found or not online."); return; }
        plugin.getApi().resetRank(t, s);
        s.sendMessage("§aReset §e" + t.getName() + "§a's rank to default.");
    }

    private void doForce(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage("§cUsage: /rank force <player> <rank>"); return; }
        Player t = Bukkit.getPlayer(args[1]);
        if (t == null) { s.sendMessage("§cPlayer not found or not online."); return; }
        plugin.getApi().setRank(t, args[2], s);
        s.sendMessage("§aForce rank §e" + args[2] + " §aapplied to §e" + t.getName() + "§a.");
    }

    private void doLang(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cUsage: /rank lang <set|list|reset>"); return; }
        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (args.length < 3) { p.sendMessage("§cProvide a language code."); return; }
                if (!plugin.getLangManager().isValidLang(args[2])) {
                    p.sendMessage("§cUnknown language: §e" + args[2] + "§c. Use §e/rank lang list§c."); return;
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
            default -> p.sendMessage("§cUsage: /rank lang <set|list|reset>");
        }
    }

    private void doDebug(Player p) {
        long xp = plugin.getExperienceManager() != null ? plugin.getExperienceManager().getXp(p) : 0L;
        plugin.getLangManager().send(p, "debug_info", Map.of(
                "rank", getCurrentRank(p),
                "db",   String.valueOf(plugin.getDatabaseManager().isConnected()),
                "lang", plugin.getLangManager().getPlayerLang(p.getUniqueId())));
        p.sendMessage("§7XP: §a" + String.format("%,d", xp));
        if (plugin.getHistoryManager() != null) {
            p.sendMessage("§7Rank-ups: §a" + plugin.getHistoryManager().countRankups(p.getUniqueId()));
        }
        if (plugin.getUpdateNotifier() != null && plugin.getUpdateNotifier().isUpdateAvailable()) {
            p.sendMessage("§e⚠ Update available: v" + plugin.getUpdateNotifier().getLatestVersion());
        }
    }

    private void doStats(CommandSender s) {
        String storageType = plugin.getDatabaseManager().isConnected() ? "§aMySQL" : "§eYAML File";
        String mcVer = org.bukkit.Bukkit.getBukkitVersion().split("-")[0];
        s.sendMessage("§8§m                                ");
        s.sendMessage("  §6§lRankForge §7System Stats");
        s.sendMessage("§8§m                                ");
        s.sendMessage("  §7Cache size:     §e" + plugin.getRankManager().getCacheManager().size());
        s.sendMessage("  §7Ranks loaded:   §e" + plugin.getRankManager().getRankCount());
        s.sendMessage("  §7Challenges:     §e" + plugin.getChallengeManager().getAllChallenges().size());
        s.sendMessage("  §7Quests:         §e" + plugin.getQuestManager().getAllQuests().size());
        s.sendMessage("  §7Expansions:     §e" + plugin.getExpansionRegistry().size());
        s.sendMessage("  §7Custom Reqs:    §e" + plugin.getCustomRequirementRegistry().size());
        s.sendMessage("  §7Hooks:          §e" + plugin.getHookRegistry().size());
        s.sendMessage("  §7Storage:        " + storageType);
        s.sendMessage("  §7MC version:     §e" + mcVer);
        s.sendMessage("  §7Vault:          " + (plugin.getSoftDependency().hasVault()     ? "§a✔" : "§7—"));
        s.sendMessage("  §7LuckPerms:      " + (plugin.getSoftDependency().hasLuckPerms() ? "§a✔" : "§7—"));
        s.sendMessage("  §7PlaceholderAPI: " + (plugin.getSoftDependency().hasPapi()      ? "§a✔" : "§7—"));
        s.sendMessage("  §7REST API:       " + (plugin.getRestAPIServer().isRunning()      ? "§a✔" : "§7—"));
        s.sendMessage("  §7Update avail:   " + (plugin.getUpdateNotifier() != null && plugin.getUpdateNotifier().isUpdateAvailable() ? "§e✔ v" + plugin.getUpdateNotifier().getLatestVersion() : "§7—"));
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
        if (args.length < 2) { p.sendMessage("§cUsage: /rank sound <test|reload>"); return; }
        switch (args[1].toLowerCase()) {
            case "test"   -> { plugin.getSoundManager().playTest(p); plugin.getLangManager().send(p, "sound_test"); }
            case "reload" -> { plugin.getSoundManager().reload();    plugin.getLangManager().send(p, "sound_reload"); }
            default       -> p.sendMessage("§cUsage: /rank sound <test|reload>");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requirePlayer(CommandSender s, Consumer<Player> action) {
        if (s instanceof Player p) action.accept(p);
        else s.sendMessage("§cPlayers only.");
    }

    private boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node) || s.isOp()) return true;
        s.sendMessage("§cNo permission: §e" + node);
        return false;
    }

    private String getCurrentRank(Player p) {
        var cache = plugin.getRankManager().getCacheManager();
        return cache.contains(p.getUniqueId())
                ? cache.get(p.getUniqueId()).rankId()
                : plugin.getRankManager().getDefaultRankId();
    }

    // ── Tab Completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("up","progress","next","current","requirements","help",
                    "editor","set","reset","force","lang","reload","debug","stats","top",
                    "security","sound","version","playerlist","daily","history","challenges",
                    "quests","xp","leaderboard"), args[0]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "editor" -> {
                    List<String> opts = new ArrayList<>(plugin.getRankManager().getRankIds());
                    opts.addAll(List.of("open","drag","save","reload"));
                    return filter(opts, args[1]);
                }
                case "lang"         -> { return filter(List.of("set","list","reset"), args[1]); }
                case "set","reset","force" -> {
                    return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
                }
                case "xp"           -> { return filter(List.of("set","add"), args[1]); }
                case "sound"        -> { return filter(List.of("test","reload"), args[1]); }
                case "leaderboard","top" -> { return filter(List.of("xp","rank"), args[1]); }
            }
        }
        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "set","force" -> { return filter(new ArrayList<>(plugin.getRankManager().getRankIds()), args[2]); }
                case "xp"          -> { return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]); }
                case "lang"        -> {
                    if (args[1].equalsIgnoreCase("set"))
                        return filter(plugin.getLangManager().getAvailableLangs(), args[2]);
                }
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream().filter(e -> e.toLowerCase().startsWith(prefix.toLowerCase())).toList();
    }
}
