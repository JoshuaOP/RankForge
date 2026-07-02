package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.CacheManager;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.gui.PlayerListGUI;
import com.joshuaop.rankforge.gui.RankDetailEditorGUI;
import com.joshuaop.rankforge.permission.PermissionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Contains all administrator subcommand implementations for /rank.
 *
 * Accessible exclusively through RankCommand, which acts as the
 * main dispatcher. No new command is registered — all admin
 * subcommands remain reachable via /rank <subcommand>.
 *
 * Admin subcommands handled here:
 *   editor, create, delete/remove, set, reset, force,
 *   reload, debug, stats, security, sound, playerlist,
 *   xp set/add (admin variant)
 */
public class RankAdminCommand {

    private final RankForge          plugin;
    private final RankEditorCommand  editorCmd;
    private final RankReloadCommand  reloadCmd;

    private static final Set<String> RESERVED_RANK_NAMES = Set.of(
            "null", "none", "default", "cancel", "all", "reset", "admin"
    );

    public RankAdminCommand(RankForge plugin,
                            RankEditorCommand editorCmd,
                            RankReloadCommand reloadCmd) {
        this.plugin    = plugin;
        this.editorCmd = editorCmd;
        this.reloadCmd = reloadCmd;
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    /**
     * Dispatches an admin subcommand.
     * args[0] is the subcommand name; args[1..] are its arguments.
     * Returns true when the subcommand was recognised (even if it failed
     * validation), false if it is unknown.
     */
    public boolean handle(CommandSender sender, String[] args) {
        return switch (args[0].toLowerCase()) {
            case "editor"          -> { if (perm(sender, PermissionRegistry.EDITOR))      editorCmd.handle(sender, args); yield true; }
            case "create"          -> { if (perm(sender, PermissionRegistry.CREATE))      requirePlayer(sender, p -> doCreate(p, args)); yield true; }
            case "delete", "remove"-> { if (perm(sender, PermissionRegistry.DELETE))      requirePlayer(sender, p -> doDelete(p, args)); yield true; }
            case "set"             -> { if (perm(sender, PermissionRegistry.SET))         doSet(sender, args); yield true; }
            case "reset"           -> { if (perm(sender, PermissionRegistry.RESET))       doReset(sender, args); yield true; }
            case "force"           -> { if (perm(sender, PermissionRegistry.FORCE))       doForce(sender, args); yield true; }
            case "reload"          -> { if (perm(sender, PermissionRegistry.RELOAD))      reloadCmd.handle(sender); yield true; }
            case "debug"           -> { requirePlayer(sender, p -> { if (perm(p, PermissionRegistry.DEBUG)) doDebug(p); }); yield true; }
            case "stats"           -> { if (perm(sender, PermissionRegistry.STATS))       doStats(sender); yield true; }
            case "security"        -> { if (perm(sender, PermissionRegistry.SECURITY))    doSecurity(sender); yield true; }
            case "sound"           -> { requirePlayer(sender, p -> { if (perm(p, PermissionRegistry.SOUND)) doSound(p, args); }); yield true; }
            case "playerlist"      -> { if (perm(sender, PermissionRegistry.PLAYER_LIST)) requirePlayer(sender, this::openPlayerList); yield true; }
            default                -> false;
        };
    }

    /**
     * Handles the admin branch of /rank xp: §e/rank xp set <player> <amount>
     * and §e/rank xp add <player> <amount>.
     * Caller must already verify that args[1] is "set" or "add" and args.length >= 4.
     */
    public void handleXpAdmin(CommandSender s, String[] args) {
        if (!perm(s, PermissionRegistry.XP_ADMIN)) return;

        String targetName = args[2];
        if (!isValidPlayerName(targetName)) {
            s.sendMessage("§c✘ Invalid player name: §e" + targetName);
            return;
        }

        Player t = Bukkit.getPlayer(targetName);
        if (t == null) {
            s.sendMessage("§c✘ Player §e" + targetName + " §cis not online.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            s.sendMessage("§c✘ Invalid amount: §e" + args[3] + " §c(must be a whole number).");
            return;
        }
        if (amount < 0) {
            s.sendMessage("§c✘ XP amount cannot be negative.");
            return;
        }

        if (args[1].equalsIgnoreCase("set")) {
            plugin.getExperienceManager().set(t, amount);
            s.sendMessage("§a✔ Set §e" + t.getName() + "§a's experience to §e" + amount + "§a.");
        } else {
            plugin.getExperienceManager().award(t, amount);
            s.sendMessage("§a✔ Added §e" + amount + " §aexperience to §e" + t.getName() + "§a.");
        }
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    /**
     * Returns admin-specific tab completions.
     * Called by RankCommand.onTabComplete when the sender is an admin.
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "editor"          -> filter(List.of("reload", "drag"), args[1]);
                case "set", "reset", "force" -> {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    yield filter(names, args[1]);
                }
                case "delete", "remove" -> filter(new ArrayList<>(plugin.getRankManager().getRankIds()), args[1]);
                case "sound"           -> filter(List.of("test", "reload"), args[1]);
                default                -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "set", "force" -> filter(new ArrayList<>(plugin.getRankManager().getRankIds()), args[2]);
                case "xp" -> {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    yield filter(names, args[2]);
                }
                default -> List.of();
            };
        }

        return List.of();
    }

    // ── Admin subcommand implementations ──────────────────────────────────────

    private void openPlayerList(Player p) {
        new PlayerListGUI(plugin).open(p);
    }

    private void doCreate(Player p, String[] args) {
        if (args.length >= 2) {
            String rankId = args[1].trim();
            String nameError = validateNewRankId(rankId);
            if (nameError != null) {
                p.sendMessage("§c✘ " + nameError);
                return;
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
            p.sendMessage("§cUsage: §e/rank delete <rankId>");
            return;
        }
        String rankId = args[1].trim();
        if (rankId.isBlank()) {
            p.sendMessage("§c✘ Rank ID cannot be blank.");
            return;
        }
        if (plugin.getRankManager().getRank(rankId) == null) {
            p.sendMessage("§c✘ Rank §e" + rankId + " §cdoes not exist.");
            return;
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
            s.sendMessage("§cUsage: §e/rank set <player> <rank>");
            return;
        }
        String targetName = args[1].trim();
        String rankId     = args[2].trim();

        if (!isValidPlayerName(targetName)) {
            s.sendMessage("§c✘ Invalid player name: §e" + targetName);
            return;
        }
        if (rankId.isBlank()) {
            s.sendMessage("§c✘ Rank ID cannot be blank.");
            return;
        }
        if (plugin.getRankManager().getRank(rankId) == null) {
            s.sendMessage("§c✘ Rank §e" + rankId + " §cdoes not exist.");
            suggestSimilarRank(s, rankId);
            return;
        }

        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            boolean ok = plugin.getApi().setRank(online, rankId, s);
            s.sendMessage(ok
                    ? "§a✔ Rank set to §e" + rankId + " §afor §e" + online.getName() + "§a."
                    : "§c✘ Rank change was cancelled by an event listener.");
            return;
        }

        applyOfflineRankChange(s, targetName, rankId, "SET");
    }

    /**
     * /rank reset <player>
     * Supports offline players.
     */
    private void doReset(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage("§cUsage: §e/rank reset <player>");
            return;
        }
        String targetName = args[1].trim();
        if (!isValidPlayerName(targetName)) {
            s.sendMessage("§c✘ Invalid player name: §e" + targetName);
            return;
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
            s.sendMessage("§cUsage: §e/rank force <player> <rank>");
            return;
        }
        String targetName = args[1].trim();
        String rankId     = args[2].trim();

        if (!isValidPlayerName(targetName)) {
            s.sendMessage("§c✘ Invalid player name: §e" + targetName);
            return;
        }
        if (rankId.isBlank()) {
            s.sendMessage("§c✘ Rank ID cannot be blank.");
            return;
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

        // Offline path — applyOfflineRankChange sends its own success message
        applyOfflineRankChange(s, targetName, rankId, "SET");
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
        s.sendMessage("  §7Vault:          " + (plugin.getSoftDependency().hasVault()     ? "§a✔" : "§7—"));
        s.sendMessage("  §7LuckPerms:      " + (plugin.getSoftDependency().hasLuckPerms() ? "§a✔" : "§7—"));
        s.sendMessage("  §7PlaceholderAPI: " + (plugin.getSoftDependency().hasPapi()      ? "§a✔" : "§7—"));
        s.sendMessage("  §7Floodgate:      " + (plugin.getSoftDependency().hasFloodgate() ? "§a✔" : "§7—"));
        s.sendMessage("  §7REST API:       " + (plugin.getRestAPIServer().isRunning()     ? "§a✔" : "§7—"));
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
        if (args.length < 2) {
            p.sendMessage("§cUsage: §e/rank sound <test|reload>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "test"   -> { plugin.getSoundManager().playTest(p); plugin.getLangManager().send(p, "sound_test"); }
            case "reload" -> { plugin.getSoundManager().reload();    plugin.getLangManager().send(p, "sound_reload"); }
            default       ->   p.sendMessage("§cUsage: §e/rank sound <test|reload>");
        }
    }

    // ── Offline rank change ───────────────────────────────────────────────────

    /**
     * Applies a rank change to an offline player by resolving their UUID,
     * updating cache + storage, and recording history.
     * Sends its own success/failure messages on the main thread.
     */
    private void applyOfflineRankChange(CommandSender s, String targetName,
                                        String rankId, String changeType) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID targetUuid = resolveOfflineUUID(targetName);

                if (targetUuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            s.sendMessage("§c✘ Player §e" + targetName
                                    + " §chas no stored data and is not online."));
                    return;
                }

                CacheManager cache = plugin.getRankManager().getCacheManager();
                PlayerData current = cache.get(targetUuid);
                if (current == null) {
                    current = plugin.getRankManager().getRepository().load(targetUuid, targetName);
                }

                if (current == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            s.sendMessage("§c✘ Could not load data for §e" + targetName + "§c."));
                    return;
                }

                String prevRank    = current.rankId();
                PlayerData updated = current.withRank(rankId);
                cache.put(targetUuid, updated);
                plugin.getRankManager().getRepository().save(updated);

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
                    Player nowOnline = Bukkit.getPlayer(finalUuid);
                    if (nowOnline != null) {
                        nowOnline.sendMessage("§6[RankForge] §7An admin has "
                                + finalCTLabel + " your rank to §e" + finalRank + "§7.");
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Offline rank change failed for "
                        + targetName + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        s.sendMessage("§c✘ An internal error occurred while updating "
                                + targetName + "'s rank."));
            }
        });
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    /**
     * Validates a player name, supporting Java and Floodgate/Bedrock players.
     */
    private boolean isValidPlayerName(String name) {
        if (name == null || name.isBlank()) return false;
        String prefix = plugin.getConfig().getString("crossplay.bedrock-prefix", ".");
        String base   = name.startsWith(prefix) ? name.substring(prefix.length()) : name;
        if (base.isEmpty()) return false;
        int maxLen = 16 + prefix.length();
        return name.length() <= maxLen && base.matches("[a-zA-Z0-9_ ]+");
    }

    /**
     * Returns an error message if the rank ID is invalid for creation, or null if acceptable.
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
     *   1. Checking the in-memory cache.
     *   2. Checking YAML player data storage.
     *   3. Calling Bukkit.getOfflinePlayer (only for previously-seen players).
     */
    @SuppressWarnings("deprecation")
    private UUID resolveOfflineUUID(String name) {
        for (var entry : plugin.getRankManager().getCacheManager().getCache().entrySet()) {
            PlayerData pd = entry.getValue().data();
            if (pd != null && name.equalsIgnoreCase(pd.playerName())) {
                return entry.getKey();
            }
        }

        if (!plugin.getDatabaseManager().isConnected()
                && plugin.getYamlPlayerDataStorage() != null) {
            for (PlayerData pd : plugin.getYamlPlayerDataStorage().loadAll()) {
                if (name.equalsIgnoreCase(pd.playerName())) {
                    return pd.uuid();
                }
            }
        }

        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            if (op.hasPlayedBefore()) {
                return op.getUniqueId();
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Suggests rank IDs similar to the given invalid ID.
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

    // ── Utility ───────────────────────────────────────────────────────────────

    private String getCurrentRank(Player p) {
        PlayerData data = plugin.getRankManager().getCacheManager().get(p.getUniqueId());
        return data != null ? data.rankId() : plugin.getRankManager().getDefaultRankId();
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

    private List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isBlank()) return options;
        String lower = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
