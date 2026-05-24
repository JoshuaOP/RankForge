package com.joshuaop.rankforge.command;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.gui.AdminRankEditorGUI;
import com.joshuaop.rankforge.gui.DragDropRankEditorGUI;
import com.joshuaop.rankforge.gui.RankDetailEditorGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles all /rank editor subcommands.
 *
 * Usage:
 *   /rank editor              → Open admin overview GUI
 *   /rank editor <rankId>     → Open detail editor for that rank
 *   /rank editor drag         → Open drag-drop slot editor
 *   /rank editor save         → Save all ranks to ranks.yml
 *   /rank editor reload       → Hot-reload ranks.yml
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

        if (!player.hasPermission("rankforge.rank.editor")) {
            plugin.getLangManager().send(player, "no_permission");
            return;
        }

        // No sub-arg → open overview
        if (args.length < 2) {
            new AdminRankEditorGUI(plugin).open(player);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "open", "gui"  -> new AdminRankEditorGUI(plugin).open(player);
            case "drag"         -> new DragDropRankEditorGUI(plugin).open(player);
            case "save"         -> handleSave(player);
            case "reload"       -> handleReload(player);
            default             -> {
                // Try to open a rank by ID (e.g., /rank editor Guest)
                openDetailGUI(player, args[1]);
            }
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

    private void handleSave(Player player) {
        if (!player.hasPermission("rankforge.rank.editor.save")) {
            plugin.getLangManager().send(player, "no_permission");
            return;
        }
        player.sendMessage("§7Saving ranks to ranks.yml…");
        plugin.getRankYamlManager().saveAsync(() ->
                player.sendMessage("§a✔ Ranks saved successfully to ranks.yml!"));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("rankforge.rank.reload")) {
            plugin.getLangManager().send(player, "no_permission");
            return;
        }
        plugin.getRankYamlManager().hotReload();
        plugin.getRankManager().loadRanks();
        player.sendMessage("§a✔ Ranks hot-reloaded from ranks.yml!");
    }
}
