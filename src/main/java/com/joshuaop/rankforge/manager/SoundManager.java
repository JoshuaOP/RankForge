package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Plays configurable sounds to players on rankup, GUI open, and GUI click.
 */
public class SoundManager {

    private final RankForge plugin;

    private Sound rankupSound;
    private float rankupVolume, rankupPitch;

    private Sound clickSound;
    private float clickVolume, clickPitch;

    private Sound openSound;
    private float openVolume, openPitch;

    private boolean enabled;

    public SoundManager(RankForge plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled      = plugin.getConfig().getBoolean("sound.enabled", true);
        rankupSound  = parseSound("sound.rankup.sound",    "ENTITY_PLAYER_LEVELUP");
        rankupVolume = (float) plugin.getConfig().getDouble("sound.rankup.volume", 1.0);
        rankupPitch  = (float) plugin.getConfig().getDouble("sound.rankup.pitch",  1.0);

        clickSound   = parseSound("sound.gui-click.sound", "UI_BUTTON_CLICK");
        clickVolume  = (float) plugin.getConfig().getDouble("sound.gui-click.volume", 0.5);
        clickPitch   = (float) plugin.getConfig().getDouble("sound.gui-click.pitch",  1.0);

        openSound    = parseSound("sound.gui-open.sound",  "BLOCK_CHEST_OPEN");
        openVolume   = (float) plugin.getConfig().getDouble("sound.gui-open.volume", 0.7);
        openPitch    = (float) plugin.getConfig().getDouble("sound.gui-open.pitch",  1.0);
    }

    public void playRankup(Player player) {
        if (enabled && rankupSound != null)
            player.playSound(player.getLocation(), rankupSound, rankupVolume, rankupPitch);
    }

    public void playClick(Player player) {
        if (enabled && clickSound != null)
            player.playSound(player.getLocation(), clickSound, clickVolume, clickPitch);
    }

    public void playOpen(Player player) {
        if (enabled && openSound != null)
            player.playSound(player.getLocation(), openSound, openVolume, openPitch);
    }

    public void playTest(Player player) {
        if (rankupSound != null)
            player.playSound(player.getLocation(), rankupSound, 1.0f, 1.0f);
    }

    private Sound parseSound(String path, String fallback) {
        String name = plugin.getConfig().getString(path, fallback);
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Sound] Unknown sound: " + name + " — using fallback.");
            try { return Sound.valueOf(fallback); } catch (Exception ex) { return null; }
        }
    }

    public boolean isEnabled() { return enabled; }
}
