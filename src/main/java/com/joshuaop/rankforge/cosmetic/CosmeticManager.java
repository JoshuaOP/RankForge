package com.joshuaop.rankforge.cosmetic;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.entity.Player;

/**
 * Central coordinator for all cosmetic systems.
 * Called by RankForge on rankup and player events.
 */
public class CosmeticManager {

    private final RankForge      plugin;
    private final BossBarManager bossBarManager;
    private final ParticleManager particleManager;
    private final TablistManager  tablistManager;
    private final JoinQuitManager joinQuitManager;

    public CosmeticManager(RankForge plugin) {
        this.plugin          = plugin;
        this.bossBarManager  = new BossBarManager(plugin);
        this.particleManager = new ParticleManager(plugin);
        this.tablistManager  = new TablistManager(plugin);
        this.joinQuitManager = new JoinQuitManager(plugin);
    }

    /** Register event listeners for cosmetics that listen to player events. */
    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(joinQuitManager, plugin);
    }

    /**
     * Apply all cosmetic effects when a player ranks up.
     *
     * @param player     the player who ranked up
     * @param newRankId  the new rank ID
     * @param display    display name of the new rank
     */
    public void onRankup(Player player, String newRankId, String display) {
        bossBarManager.showRankupBar(player, display);
        particleManager.startTrail(player, newRankId);
        tablistManager.update(player, newRankId);
    }

    /** Restore cosmetics when a player logs in. */
    public void onLogin(Player player, String rankId) {
        particleManager.startTrail(player, rankId);
        tablistManager.update(player, rankId);
    }

    /** Remove all cosmetics for a player on logout. */
    public void onLogout(Player player) {
        bossBarManager.removeBar(player);
        particleManager.stopTrail(player);
        tablistManager.reset(player);
    }

    /** Shutdown all cosmetic systems cleanly. */
    public void shutdown() {
        bossBarManager.removeAll();
        particleManager.stopAll();
    }

    public BossBarManager  getBossBarManager()  { return bossBarManager; }
    public ParticleManager getParticleManager() { return particleManager; }
    public TablistManager  getTablistManager()  { return tablistManager; }
    public JoinQuitManager getJoinQuitManager() { return joinQuitManager; }
}
