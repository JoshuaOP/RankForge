package com.joshuaop.rankforge.permission;

/**
 * Central registry of all static RankForge permission nodes.
 * Use these constants everywhere — never hardcode permission strings.
 */
public final class PermissionRegistry {

    private PermissionRegistry() {}

    public static final String BASE         = "rankforge";
    public static final String STAR         = BASE + ".*";

    // ── Player Permissions ────────────────────────────────────────────────────
    public static final String USE          = BASE + ".rank.use";
    public static final String RANK_UP      = BASE + ".rank.up";
    public static final String PROGRESS     = BASE + ".rank.progress";
    public static final String NEXT         = BASE + ".rank.next";
    public static final String CURRENT      = BASE + ".rank.current";
    public static final String REQUIREMENTS = BASE + ".rank.requirements";
    public static final String LANG         = BASE + ".rank.lang";
    public static final String HISTORY      = BASE + ".rank.history";

    // ── Admin Permissions ─────────────────────────────────────────────────────
    public static final String EDITOR       = BASE + ".rank.editor";
    public static final String EDITOR_SAVE  = BASE + ".rank.editor.save";
    public static final String EDITOR_DRAG  = BASE + ".rank.editor.drag";
    public static final String CREATE       = BASE + ".rank.create";
    public static final String DELETE       = BASE + ".rank.delete";
    public static final String SET          = BASE + ".rank.set";
    public static final String RESET        = BASE + ".rank.reset";
    public static final String FORCE        = BASE + ".rank.force";
    public static final String RELOAD       = BASE + ".rank.reload";
    public static final String DEBUG        = BASE + ".rank.debug";
    public static final String STATS        = BASE + ".rank.stats";
    public static final String SECURITY     = BASE + ".rank.security";
    public static final String SOUND        = BASE + ".rank.sound";
    public static final String XP_ADMIN     = BASE + ".rank.xp.admin";
    public static final String VERSION      = BASE + ".rank.system.version";
    public static final String PLAYER_LIST  = BASE + ".rank.playerlist";

    /**
     * All static permission nodes iterated by {@link PermissionNodeGenerator} at startup.
     */
    public static final String[] ALL_NODES = {
            USE, RANK_UP, PROGRESS, NEXT, CURRENT, REQUIREMENTS, LANG, HISTORY,
            EDITOR, EDITOR_SAVE, EDITOR_DRAG,
            CREATE, DELETE,
            SET, RESET, FORCE, RELOAD, DEBUG, STATS, SECURITY,
            SOUND, XP_ADMIN, VERSION, PLAYER_LIST
    };
}
