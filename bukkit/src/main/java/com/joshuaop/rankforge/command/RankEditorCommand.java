package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.gui.AdminRankEditorGUI;
import com.joshuaop.rankforge.gui.DragDropRankEditorGUI;
import com.joshuaop.rankforge.gui.RankDetailEditorGUI;
import com.joshuaop.rankforge.permission.PermissionRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles all /rank editor subcommands.
 *
 * Usage:
 *   /rank editor              → Open admin overview GUI
 *   /rank editor <rankId>     → Open detail editor for that rank
 *   /rank editor drag         → Open drag-drop slot editor
 *   /rank editor reload       → Hot-reload ranks.yml
 *
 * Ranks are saved automatically whenever any change is made.
 * There is no manual save command.
 *
 * Required permission: {@link PermissionRegistry#ADMIN_EDITOR} (rankforge.admin.editor)
 */
public class RankEditorCommand {

    private final RankForge plugin;

    public RankEditorCommand(RankForge plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command is for players only.");
            return;
        }

        if (!player.hasPermission(PermissionRegistry.ADMIN_EDITOR)) {
            plugin.getLangManager().send(player, "no_permission");
            return;
        }

        if (args.length < 2) {
            new AdminRankEditorGUI(plugin).open(player);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "open", "gui"  -> new AdminRankEditorGUI(plugin).open(player);
            case "drag"         -> new DragDropRankEditorGUI(plugin).open(player);
            case "reload"       -> handleReload(player);
            default             -> openDetailGUI(player, args[1]);
        }
    }

    private void openDetailGUI(Player player, String rankId) {
        boolean opened = new RankDetailEditorGUI(plugin).open(player, rankId);
        if (!opened) {
            player.sendMessage("§cRank §e" + rankId + " §cnot found in ranks.yml.");
            player.sendMessage("§7Available ranks: §e"
                    + String.join(", ", plugin.getRankManager().getRankIds()));
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission(PermissionRegistry.ADMIN_RELOAD)) {
            plugin.getLangManager().send(player, "no_permission");
            return;
        }
        plugin.getRankYamlManager().hotReload();
        plugin.getRankManager().loadRanks();
        player.sendMessage("§a✔ Ranks hot-reloaded from ranks.yml!");
    }
}
