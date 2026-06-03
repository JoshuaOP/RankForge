package com.joshuaop.rankforge.gui;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Loads and exposes GUI settings from plugins/RankForge/gui.yml.
 * Supports & color codes in all title strings.
 * Reload via plugin.getGUIConfig().load() on hot-reload.
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

    // ── Player List GUI ───────────────────────────────────────────────────────
    public String   playerListTitle()     { return c(cfg.getString("player-list.title",            "&8✦ &9Player List &8✦")); }
    public Material playerListBorder()    { return m(cfg.getString("player-list.border-material",  "BLUE_STAINED_GLASS_PANE")); }
    public int      playerListPrevSlot()  { return   cfg.getInt   ("player-list.prev-page-slot",   45); }
    public int      playerListCloseSlot() { return   cfg.getInt   ("player-list.close-slot",       49); }
    public int      playerListNextSlot()  { return   cfg.getInt   ("player-list.next-page-slot",   53); }

    // ── Common ────────────────────────────────────────────────────────────────
    public Material fillerMaterial()      { return m(cfg.getString("common.filler-material",       "GRAY_STAINED_GLASS_PANE")); }
    public Material closeMaterial()       { return m(cfg.getString("common.close-material",        "BARRIER")); }
    public String   closeName()           { return c(cfg.getString("common.close-name",            "&cClose")); }
    public Material backMaterial()        { return m(cfg.getString("common.back-material",         "ARROW")); }
    public String   backName()            { return c(cfg.getString("common.back-name",             "&7Back")); }

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
