package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Loads and exposes all GUI settings from plugins/RankForge/gui.yml.
 *
 * <p>Covers layout (titles, borders, slots, materials), window sizing
 * (rows), and interaction protection (gui-click-shield). Settings that
 * previously lived in config.yml are automatically migrated here on
 * first load so server owners do not need to reconfigure anything.
 *
 * <p>Supports {@code &} color codes in all title strings.
 * Reload via {@code plugin.getGuiConfig().load()} on hot-reload.
 */
public class GUIConfig {

    private final RankForge        plugin;
    private YamlConfiguration      cfg;

    public GUIConfig(RankForge plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
        migrateFromConfig(file);
    }

    // ── Player GUI ────────────────────────────────────────────────────────────
    public String   playerTitle()         { return c(cfg.getString("player-gui.title",            "&8✦ &6RankForge &8✦")); }
    public Material playerBorder()        { return m(cfg.getString("player-gui.border-material",   "CYAN_STAINED_GLASS_PANE")); }
    public int      playerHeadSlot()      { return   cfg.getInt   ("player-gui.head-slot",         4); }
    public int      playerInfoSlot()      { return   cfg.getInt   ("player-gui.info-slot",         49); }

    // ── Admin Editor GUI ──────────────────────────────────────────────────────
    public String   adminTitle()          { return c(cfg.getString("admin-gui.title",              "&8✦ &cAdmin Rank Editor &8✦")); }
    public Material adminBorder()         { return m(cfg.getString("admin-gui.border-material",    "RED_STAINED_GLASS_PANE")); }

    // ── Detail Editor GUI ─────────────────────────────────────────────────────
    public String   detailTitlePrefix()   { return c(cfg.getString("detail-editor.title-prefix",   "&8✦ &6Editing: ")); }
    public Material detailBorderTop()     { return m(cfg.getString("detail-editor.border-top",     "LIME_STAINED_GLASS_PANE")); }
    public Material detailBorderBot()     { return m(cfg.getString("detail-editor.border-bottom",  "GRAY_STAINED_GLASS_PANE")); }

    // ── Drag-Drop GUI ─────────────────────────────────────────────────────────
    public String   dragDropTitle()       { return c(cfg.getString("drag-drop.title",              "&8✦ &bSlot Editor &8✦")); }

    // ── Player Data Editor GUI ────────────────────────────────────────────────
    public String   playerDataEditorTitlePrefix() { return c(cfg.getString("player-data-editor.title-prefix", "&8✦ &9Editing: ")); }

    // ── Player List GUI ───────────────────────────────────────────────────────
    public String   playerListTitle()     { return c(cfg.getString("player-list.title",            "&8✦ &9Player List &8✦")); }
    public Material playerListBorder()    { return m(cfg.getString("player-list.border-material",  "BLUE_STAINED_GLASS_PANE")); }
    public int      playerListPrevSlot()  { return   cfg.getInt   ("player-list.prev-page-slot",   45); }
    public int      playerListCloseSlot() { return   cfg.getInt   ("player-list.close-slot",       49); }
    public int      playerListNextSlot()  { return   cfg.getInt   ("player-list.next-page-slot",   53); }

    // ── Window Size ───────────────────────────────────────────────────────────
    /** Number of rows in the player-facing rank tree GUI (1–6). */
    public int      rows()                { return   cfg.getInt   ("gui.rows",                     6); }
    /** Number of rows in the admin rank editor GUI (1–6). */
    public int      adminRows()           { return   cfg.getInt   ("gui.admin-rows",               6); }

    // ── GUI Click Shield ──────────────────────────────────────────────────────
    /** Whether the per-player GUI click cooldown is active. */
    public boolean  clickShieldEnabled()  { return   cfg.getBoolean("gui-click-shield.enabled",    true); }
    /** Minimum milliseconds between two accepted GUI clicks for the same player. */
    public long     clickShieldCooldownMs(){ return  cfg.getLong   ("gui-click-shield.cooldown-ms", 400L); }

    // ── Common ────────────────────────────────────────────────────────────────
    public Material fillerMaterial()      { return m(cfg.getString("common.filler-material",       "GRAY_STAINED_GLASS_PANE")); }
    public Material closeMaterial()       { return m(cfg.getString("common.close-material",        "BARRIER")); }
    public String   closeName()           { return c(cfg.getString("common.close-name",            "&cClose")); }
    public Material backMaterial()        { return m(cfg.getString("common.back-material",         "ARROW")); }
    public String   backName()            { return c(cfg.getString("common.back-name",             "&7Back")); }

    // ── Migration ─────────────────────────────────────────────────────────────

    /**
     * One-time automatic migration: copies any GUI-related keys that still exist
     * in config.yml (from an older installation) into gui.yml, so server owners
     * do not need to reconfigure their settings manually after upgrading.
     *
     * <p>Only runs when the target key is absent from gui.yml AND the source key
     * is present in config.yml with a non-default-looking value. The migrated
     * values are persisted to disk immediately so the migration only fires once.
     */
    private void migrateFromConfig(File guiFile) {
        FileConfiguration mainCfg = plugin.getConfig();
        boolean dirty = false;

        // ── gui-click-shield ──────────────────────────────────────────────────
        if (!cfg.contains("gui-click-shield.enabled") && mainCfg.contains("gui-click-shield.enabled")) {
            cfg.set("gui-click-shield.enabled",     mainCfg.getBoolean("gui-click-shield.enabled",     true));
            cfg.set("gui-click-shield.cooldown-ms", mainCfg.getLong   ("gui-click-shield.cooldown-ms", 400L));
            dirty = true;
            plugin.getLogger().info("[GUIConfig] Migrated gui-click-shield settings from config.yml → gui.yml.");
        }

        // ── gui rows ──────────────────────────────────────────────────────────
        if (!cfg.contains("gui.rows") && mainCfg.contains("gui.rows")) {
            cfg.set("gui.rows",       mainCfg.getInt("gui.rows",       6));
            cfg.set("gui.admin-rows", mainCfg.getInt("gui.admin-rows", 6));
            dirty = true;
            plugin.getLogger().info("[GUIConfig] Migrated gui row settings from config.yml → gui.yml.");
        }

        if (dirty) {
            try {
                cfg.save(guiFile);
            } catch (IOException e) {
                plugin.getLogger().warning("[GUIConfig] Could not persist migrated GUI settings to gui.yml: " + e.getMessage());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String c(String s) {
        return s != null ? s.replace("&", "§") : "";
    }

    private Material m(String name) {
        if (name == null) return Material.GRAY_STAINED_GLASS_PANE;
        try { return Material.valueOf(name.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.GRAY_STAINED_GLASS_PANE; }
    }
}
