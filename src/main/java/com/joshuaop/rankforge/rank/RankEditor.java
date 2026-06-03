package com.joshuaop.rankforge.rank;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.gui.PlayerDataEditorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Admin utility for modifying rank configuration values at runtime.
 * Every mutation auto-saves to ranks.yml via the debounced async writer.
 */
public class RankEditor {

    private final RankForge plugin;

    public RankEditor(RankForge plugin) {
        this.plugin = plugin;
    }

    public void setDisplayName(String rankId, String displayName) {
        mutate(rankId, model -> model.withDisplayName(displayName));
    }

    public void setRequirementMoney(String rankId, double amount) {
        mutate(rankId, model -> model.withRequiredMoney(amount));
    }

    public void setSlot(String rankId, int slot) {
        mutate(rankId, model -> model.withSlot(slot));
    }

    public void setNextRank(String rankId, String nextRankId) {
        mutate(rankId, model -> new RankModel.Builder(rankId)
                .displayName(model.getDisplayName())
                .nextRankId(nextRankId)
                .slot(model.getSlot())
                .material(model.getMaterial())
                .lore(model.getLore())
                .requiredMoney(model.getRequiredMoney())
                .requiredXpLevel(model.getRequiredXpLevel())
                .requiredPermission(model.getRequiredPermission())
                .permissions(model.getPermissions())
                .chatPrefix(model.getChatPrefix())
                .build());
    }

    public void setMaterial(String rankId, String material) {
        mutate(rankId, model -> new RankModel.Builder(rankId)
                .displayName(model.getDisplayName())
                .nextRankId(model.getNextRankId())
                .slot(model.getSlot())
                .material(material)
                .lore(model.getLore())
                .requiredMoney(model.getRequiredMoney())
                .requiredXpLevel(model.getRequiredXpLevel())
                .requiredPermission(model.getRequiredPermission())
                .permissions(model.getPermissions())
                .chatPrefix(model.getChatPrefix())
                .build());
    }

    /** Check if a rank ID exists in the in-memory rank table. */
    public boolean rankExists(String rankId) {
        return plugin.getRankManager().getRank(rankId) != null;
    }

    // ── Internals & UI Refreshing ─────────────────────────────────────────────

    private interface Mutator {
        RankModel apply(RankModel model);
    }

    private void mutate(String rankId, Mutator mutator) {
        RankModel model = plugin.getRankManager().getRank(rankId);
        if (model == null) return;
        RankModel updated = mutator.apply(model);
        plugin.getRankYamlManager().updateRank(updated, true);
        plugin.getRankManager().updateModel(updated);
        refreshAllActivePlayerDataGUIs();
    }

    /**
     * Loops through all online players to check if an administrator has a
     * player editor open, forcing a visual refresh if global configurations alter.
     */
    private void refreshAllActivePlayerDataGUIs() {
        for (Player onlineAdmin : Bukkit.getOnlinePlayers()) {
            String title = onlineAdmin.getOpenInventory().getTitle();

            if (PlayerDataEditorGUI.matchesTitle(title)) {
                UUID adminUuid = onlineAdmin.getUniqueId();
                if (PlayerDataEditorGUI.isOpen(adminUuid)) {
                    String targetName = title.substring(PlayerDataEditorGUI.TITLE_PREFIX.length());
                    Player targetPlayer = Bukkit.getPlayerExact(targetName);
                    if (targetPlayer != null) {
                        new PlayerDataEditorGUI(plugin).open(onlineAdmin, targetPlayer.getUniqueId(), targetName);
                    }
                }
            }
        }
    }
}
