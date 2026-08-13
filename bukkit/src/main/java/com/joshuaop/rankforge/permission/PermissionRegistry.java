package com.joshuaop.rankforge.permission;

/**
 * Central registry of all RankForge permission nodes.
 * Use these constants everywhere — never hardcode permission strings.
 *
 * <p>Permission structure:
 * <pre>
 * rankforge.*
 *   ├── rankforge.use.*
 *   │     ├── rankforge.use          (/rank)
 *   │     ├── rankforge.use.up       (/rank up)
 *   │     ├── rankforge.use.current  (/rank current)
 *   │     ├── rankforge.use.next     (/rank next)
 *   │     ├── rankforge.use.progress (/rank progress)
 *   │     ├── rankforge.use.requirements (/rank requirements)
 *   │     ├── rankforge.use.history  (/rank history)
 *   │     ├── rankforge.use.help     (/rank help)
 *   │     ├── rankforge.use.lang     (/rank lang)
 *   │     ├── rankforge.use.version  (/rank version)
 *   │     └── rankforge.use.xp      (/rank xp — self view)
 *   └── rankforge.admin.*
 *         ├── rankforge.admin.reload     (/rank reload)
 *         ├── rankforge.admin.editor     (/rank editor)
 *         ├── rankforge.admin.playerlist (/rank playerlist)
 *         ├── rankforge.admin.create     (/rank create)
 *         ├── rankforge.admin.delete     (/rank delete)
 *         ├── rankforge.admin.set        (/rank set)
 *         ├── rankforge.admin.force      (/rank force)
 *         ├── rankforge.admin.reset      (/rank reset)
 *         ├── rankforge.admin.bypassreq  (/rank bypassreq)
 *         ├── rankforge.admin.stats      (/rank stats)
 *         ├── rankforge.admin.debug      (/rank debug)
 *         ├── rankforge.admin.security   (/rank security)
 *         ├── rankforge.admin.sound      (/rank sound)
 *         └── rankforge.admin.xp        (/rank xp set|add)
 * </pre>
 */
public final class PermissionRegistry {

    private PermissionRegistry() {}

    public static final String BASE = "rankforge";
    public static final String STAR = BASE + ".*";

    // ── Player Permissions ────────────────────────────────────────────────────
    /** Wildcard — grants all player permissions. */
    public static final String USE_STAR         = BASE + ".use.*";
    /** /rank — open the rank GUI. */
    public static final String USE              = BASE + ".use";
    /** /rank up */
    public static final String USE_UP           = BASE + ".use.up";
    /** /rank current */
    public static final String USE_CURRENT      = BASE + ".use.current";
    /** /rank next */
    public static final String USE_NEXT         = BASE + ".use.next";
    /** /rank progress */
    public static final String USE_PROGRESS     = BASE + ".use.progress";
    /** /rank requirements */
    public static final String USE_REQUIREMENTS = BASE + ".use.requirements";
    /** /rank history */
    public static final String USE_HISTORY      = BASE + ".use.history";
    /** /rank help */
    public static final String USE_HELP         = BASE + ".use.help";
    /** /rank lang */
    public static final String USE_LANG         = BASE + ".use.lang";
    /** /rank version */
    public static final String USE_VERSION      = BASE + ".use.version";
    /** /rank xp (self-view) */
    public static final String USE_XP           = BASE + ".use.xp";

    // ── Admin Permissions ─────────────────────────────────────────────────────
    /** Wildcard — grants all administrator permissions. */
    public static final String ADMIN_STAR       = BASE + ".admin.*";
    /** /rank reload */
    public static final String ADMIN_RELOAD     = BASE + ".admin.reload";
    /** /rank editor */
    public static final String ADMIN_EDITOR     = BASE + ".admin.editor";
    /** /rank playerlist */
    public static final String ADMIN_PLAYER_LIST = BASE + ".admin.playerlist";
    /** /rank create */
    public static final String ADMIN_CREATE     = BASE + ".admin.create";
    /** /rank delete */
    public static final String ADMIN_DELETE     = BASE + ".admin.delete";
    /** /rank set */
    public static final String ADMIN_SET        = BASE + ".admin.set";
    /** /rank force */
    public static final String ADMIN_FORCE      = BASE + ".admin.force";
    /** /rank reset */
    public static final String ADMIN_RESET      = BASE + ".admin.reset";
    /** /rank bypassreq */
    public static final String ADMIN_BYPASSREQ  = BASE + ".admin.bypassreq";
    /** /rank stats */
    public static final String ADMIN_STATS      = BASE + ".admin.stats";
    /** /rank debug */
    public static final String ADMIN_DEBUG      = BASE + ".admin.debug";
    /** /rank security */
    public static final String ADMIN_SECURITY   = BASE + ".admin.security";
    /** /rank sound */
    public static final String ADMIN_SOUND      = BASE + ".admin.sound";
    /** /rank xp set | /rank xp add */
    public static final String ADMIN_XP         = BASE + ".admin.xp";

    // ── Backward-Compatible Nodes ─────────────────────────────────────────────
    // Retained for requirement evaluation (RequirementManager, ProgressService)
    // and Developer-API usage. NOT used for command permission gating.
    public static final String BYPASS_REQ   = BASE + ".rank.bypassreq";
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
    public static final String RANK_UP      = BASE + ".rank.up";
    public static final String PROGRESS     = BASE + ".rank.progress";
    public static final String NEXT         = BASE + ".rank.next";
    public static final String CURRENT      = BASE + ".rank.current";
    public static final String REQUIREMENTS = BASE + ".rank.requirements";
    public static final String LANG         = BASE + ".rank.lang";
    public static final String HISTORY      = BASE + ".rank.history";

    /**
     * All active permission nodes registered by {@link PermissionNodeGenerator} at startup.
     */
    public static final String[] ALL_NODES = {
            // Player
            USE_STAR, USE, USE_UP, USE_CURRENT, USE_NEXT, USE_PROGRESS,
            USE_REQUIREMENTS, USE_HISTORY, USE_HELP, USE_LANG, USE_VERSION, USE_XP,
            // Admin
            ADMIN_STAR, ADMIN_RELOAD, ADMIN_EDITOR, ADMIN_PLAYER_LIST,
            ADMIN_CREATE, ADMIN_DELETE, ADMIN_SET, ADMIN_FORCE, ADMIN_RESET,
            ADMIN_BYPASSREQ, ADMIN_STATS, ADMIN_DEBUG, ADMIN_SECURITY, ADMIN_SOUND, ADMIN_XP
    };
}
